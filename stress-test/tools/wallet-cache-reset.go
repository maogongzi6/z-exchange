package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"strings"
	"time"
)

type redisValue struct {
	text  string
	array []redisValue
}

type redisClient struct {
	connection net.Conn
	reader     *bufio.Reader
	writer     *bufio.Writer
}

func main() {
	reset := env("WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN", "false")
	if reset == "false" {
		fmt.Println("Wallet cache reset disabled.")
		return
	}
	if reset != "true" {
		fatal("WALLET_STRESS_TRUNCATE_CACHE_BEFORE_RUN must be true or false")
	}

	host := env("REDIS_HOST", "172.31.18.211")
	port := env("REDIS_PORT", "6379")
	attempts := positiveInt("REDIS_READY_ATTEMPTS", 30)
	client, err := connectWithRetry(net.JoinHostPort(host, port), attempts)
	if err != nil {
		fatal(err.Error())
	}
	defer client.connection.Close()

	if err := authenticate(client); err != nil {
		fatal("Redis authentication failed: " + err.Error())
	}
	if _, err := client.command("PING"); err != nil {
		fatal("Redis PING failed: " + err.Error())
	}
	if database := env("REDIS_DB", "0"); database != "0" {
		if _, err := client.command("SELECT", database); err != nil {
			fatal("Redis SELECT failed: " + err.Error())
		}
	}

	// Only wallet-owned key families are removed. UNLINK keeps Redis responsive
	// when a long soak has created a large idempotency-key population.
	patterns := []string{
		"balance_snapshot:id:*",
		"balance_snapshot:ref:*",
		"idemp_k:wallet:post_transaction:*",
	}
	deleted := 0
	for _, pattern := range patterns {
		count, err := deletePattern(client, pattern)
		if err != nil {
			fatal(fmt.Sprintf("failed to delete Redis pattern %q: %v", pattern, err))
		}
		deleted += count
	}
	removedSuffix := "keys"
	if deleted == 1 {
		removedSuffix = "key"
	}
	fmt.Printf("Wallet cache reset completed: unlinked %d %s.\n", deleted, removedSuffix)
}

func connectWithRetry(address string, attempts int) (*redisClient, error) {
	var lastErr error
	for attempt := 1; attempt <= attempts; attempt++ {
		connection, err := net.DialTimeout("tcp", address, 5*time.Second)
		if err == nil {
			return &redisClient{
				connection: connection,
				reader:     bufio.NewReader(connection),
				writer:     bufio.NewWriter(connection),
			}, nil
		} else {
			lastErr = err
		}
		if attempt < attempts {
			time.Sleep(2 * time.Second)
		}
	}
	return nil, fmt.Errorf("Redis at %s was not ready after %d attempts: %w", address, attempts, lastErr)
}

func authenticate(client *redisClient) error {
	password := os.Getenv("REDIS_PASSWORD")
	if password == "" {
		return nil
	}
	username := os.Getenv("REDIS_USERNAME")
	if username == "" {
		_, err := client.command("AUTH", password)
		return err
	}
	_, err := client.command("AUTH", username, password)
	return err
}

func deletePattern(client *redisClient, pattern string) (int, error) {
	cursor := "0"
	deleted := 0
	for {
		response, err := client.command("SCAN", cursor, "MATCH", pattern, "COUNT", "1000")
		if err != nil {
			return deleted, err
		}
		if len(response.array) != 2 {
			return deleted, errors.New("unexpected SCAN response")
		}
		cursor = response.array[0].text
		keys := response.array[1].array
		for start := 0; start < len(keys); start += 100 {
			end := start + 100
			if end > len(keys) {
				end = len(keys)
			}
			args := []string{"UNLINK"}
			for _, key := range keys[start:end] {
				args = append(args, key.text)
			}
			result, commandErr := client.command(args...)
			if commandErr != nil {
				return deleted, commandErr
			}
			count, parseErr := strconv.Atoi(result.text)
			if parseErr != nil {
				return deleted, fmt.Errorf("unexpected UNLINK response %q", result.text)
			}
			deleted += count
		}
		if cursor == "0" {
			return deleted, nil
		}
	}
}

func (client *redisClient) command(args ...string) (redisValue, error) {
	if err := client.connection.SetDeadline(time.Now().Add(10 * time.Second)); err != nil {
		return redisValue{}, err
	}
	if _, err := fmt.Fprintf(client.writer, "*%d\r\n", len(args)); err != nil {
		return redisValue{}, err
	}
	for _, arg := range args {
		if _, err := fmt.Fprintf(client.writer, "$%d\r\n%s\r\n", len(arg), arg); err != nil {
			return redisValue{}, err
		}
	}
	if err := client.writer.Flush(); err != nil {
		return redisValue{}, err
	}
	return readRedisValue(client.reader)
}

func readRedisValue(reader *bufio.Reader) (redisValue, error) {
	prefix, err := reader.ReadByte()
	if err != nil {
		return redisValue{}, err
	}
	line, err := readLine(reader)
	if err != nil {
		return redisValue{}, err
	}
	switch prefix {
	case '+', ':':
		return redisValue{text: line}, nil
	case '-':
		return redisValue{}, errors.New(line)
	case '$':
		length, parseErr := strconv.Atoi(line)
		if parseErr != nil || length < -1 {
			return redisValue{}, fmt.Errorf("invalid Redis bulk length %q", line)
		}
		if length == -1 {
			return redisValue{}, nil
		}
		payload := make([]byte, length+2)
		if _, readErr := io.ReadFull(reader, payload); readErr != nil {
			return redisValue{}, readErr
		}
		if string(payload[length:]) != "\r\n" {
			return redisValue{}, errors.New("invalid Redis bulk terminator")
		}
		return redisValue{text: string(payload[:length])}, nil
	case '*':
		count, parseErr := strconv.Atoi(line)
		if parseErr != nil || count < -1 {
			return redisValue{}, fmt.Errorf("invalid Redis array length %q", line)
		}
		result := redisValue{array: make([]redisValue, 0, max(count, 0))}
		for index := 0; index < count; index++ {
			item, itemErr := readRedisValue(reader)
			if itemErr != nil {
				return redisValue{}, itemErr
			}
			result.array = append(result.array, item)
		}
		return result, nil
	default:
		return redisValue{}, fmt.Errorf("unknown Redis response prefix %q", prefix)
	}
}

func readLine(reader *bufio.Reader) (string, error) {
	line, err := reader.ReadString('\n')
	if err != nil {
		return "", err
	}
	if !strings.HasSuffix(line, "\r\n") {
		return "", errors.New("invalid Redis line terminator")
	}
	return strings.TrimSuffix(line, "\r\n"), nil
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func positiveInt(name string, fallback int) int {
	raw := env(name, strconv.Itoa(fallback))
	value, err := strconv.Atoi(raw)
	if err != nil || value < 1 {
		fatal(name + " must be a positive integer")
	}
	return value
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
