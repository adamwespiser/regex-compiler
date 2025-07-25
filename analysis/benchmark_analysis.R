#!/usr/bin/env Rscript

# Regex Compiler Benchmark Analysis
# ==================================
# This script reads benchmark_results.csv and creates whisker plots comparing
# memory usage and processing time across different algorithms and data sizes.

# Load required libraries (using base R to avoid architecture issues)
library(ggplot2)
library(scales)

# Set working directory to project root
setwd("..")

# Read the benchmark data
cat("Reading benchmark_results.csv...\n")
if (!file.exists("benchmark_results.csv")) {
  stop("Error: benchmark_results.csv not found. Please run the benchmark first.")
}

data <- read.csv("benchmark_results.csv", stringsAsFactors = FALSE)

# Filter out error cases and invalid data using base R
data_clean <- data[data$status == "SUCCESS" & 
                   data$memoryUsedBytes > 0 & 
                   data$compileTimeNs > 0 & 
                   data$matchTimeNs > 0 & 
                   data$findTimeNs > 0, ]

cat(sprintf("Loaded %d successful benchmark results\n", nrow(data_clean)))

# Convert data size to factor for proper ordering
data_clean$dataSize <- factor(data_clean$dataSize, levels = c("10", "50", "100", "500",  "1000", "10000", "100000"))

# Create processing time column (average of match and find times)
data_clean$processingTimeNs <- (data_clean$matchTimeNs + data_clean$findTimeNs) / 2

# Convert to more readable units
data_clean$memoryUsedKB <- data_clean$memoryUsedBytes / 1024
data_clean$processingTimeUs <- data_clean$processingTimeNs / 1000

# Define consistent colors for algorithms
algorithm_colors <- c("DFA" = "#1f77b4", "Backtrack" = "#ff7f0e", "Table" = "#2ca02c", "JIT" = "#d62728")

# Create memory usage whisker plot
cat("Creating memory usage plot...\n")
memory_plot <- ggplot(data_clean, aes(x = dataSize, y = memoryUsedKB, fill = algorithm)) +
  geom_boxplot(position = position_dodge(width = 0.8), alpha = 0.7) +
  scale_fill_manual(values = algorithm_colors, name = "Algorithm") +
  scale_y_continuous(labels = comma_format(suffix = " KB")) +
  labs(
    title = "Memory Usage by Algorithm and Data Size",
    subtitle = "Whisker plots showing distribution of memory footprint per compiled regex pattern",
    x = "Data Size (Pattern/Input Length)",
    y = "Memory Usage (KB)",
    caption = "Boxes show quartiles, whiskers show 1.5*IQR range"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 14, face = "bold"),
    plot.subtitle = element_text(size = 11, color = "gray60"),
    legend.position = "bottom",
    panel.grid.minor = element_blank()
  )

# Create processing time whisker plot
cat("Creating processing time plot...\n")
time_plot <- ggplot(data_clean, aes(x = dataSize, y = processingTimeUs, fill = algorithm)) +
  geom_boxplot(position = position_dodge(width = 0.8), alpha = 0.7) +
  scale_fill_manual(values = algorithm_colors, name = "Algorithm") +
  scale_y_continuous(labels = comma_format(suffix = " μs")) +
  labs(
    title = "Processing Time by Algorithm and Data Size",
    subtitle = "Whisker plots showing distribution of average match/find time per operation",
    x = "Data Size (Pattern/Input Length)",
    y = "Processing Time (microseconds)",
    caption = "Boxes show quartiles, whiskers show 1.5*IQR range"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 14, face = "bold"),
    plot.subtitle = element_text(size = 11, color = "gray60"),
    legend.position = "bottom",
    panel.grid.minor = element_blank()
  )

# Create logarithmic versions of the plots
cat("Creating logarithmic scale plots...\n")
memory_plot_log <- ggplot(data_clean, aes(x = dataSize, y = memoryUsedKB, fill = algorithm)) +
  geom_boxplot(position = position_dodge(width = 0.8), alpha = 0.7) +
  scale_fill_manual(values = algorithm_colors, name = "Algorithm") +
  scale_y_log10(labels = comma_format(suffix = " KB")) +
  labs(
    title = "Memory Usage by Algorithm and Data Size (Log Scale)",
    subtitle = "Whisker plots showing distribution of memory footprint per compiled regex pattern",
    x = "Data Size (Pattern/Input Length)",
    y = "Memory Usage (KB) - Log Scale",
    caption = "Boxes show quartiles, whiskers show 1.5*IQR range"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 14, face = "bold"),
    plot.subtitle = element_text(size = 11, color = "gray60"),
    legend.position = "bottom",
    panel.grid.minor = element_blank()
  )

time_plot_log <- ggplot(data_clean, aes(x = dataSize, y = processingTimeUs, fill = algorithm)) +
  geom_boxplot(position = position_dodge(width = 0.8), alpha = 0.7) +
  scale_fill_manual(values = algorithm_colors, name = "Algorithm") +
  scale_y_log10(labels = comma_format(suffix = " μs")) +
  labs(
    title = "Processing Time by Algorithm and Data Size (Log Scale)",
    subtitle = "Whisker plots showing distribution of average match/find time per operation",
    x = "Data Size (Pattern/Input Length)",
    y = "Processing Time (microseconds) - Log Scale",
    caption = "Boxes show quartiles, whiskers show 1.5*IQR range"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(size = 14, face = "bold"),
    plot.subtitle = element_text(size = 11, color = "gray60"),
    legend.position = "bottom",
    panel.grid.minor = element_blank()
  )

# Save plots
cat("Saving plots to analysis/ directory...\n")
ggsave("analysis/memory_usage_by_algorithm.png", memory_plot, 
       width = 10, height = 6, dpi = 300, bg = "white")

ggsave("analysis/memory_usage_by_algorithm_log.png", memory_plot_log, 
       width = 10, height = 6, dpi = 300, bg = "white")

ggsave("analysis/processing_time_by_algorithm.png", time_plot, 
       width = 10, height = 6, dpi = 300, bg = "white")

ggsave("analysis/processing_time_by_algorithm_log.png", time_plot_log, 
       width = 10, height = 6, dpi = 300, bg = "white")

# Print summary statistics using base R
cat("\n")
cat(paste(rep("=", 50), collapse = ""))
cat("\nBENCHMARK ANALYSIS SUMMARY\n")
cat(paste(rep("=", 50), collapse = ""))
cat("\n")

# Memory usage summary by algorithm
cat("\nMemory Usage Summary (KB):\n")
algorithms <- unique(data_clean$algorithm)
memory_summary <- data.frame(
  algorithm = algorithms,
  min = sapply(algorithms, function(a) min(data_clean$memoryUsedKB[data_clean$algorithm == a])),
  q1 = sapply(algorithms, function(a) quantile(data_clean$memoryUsedKB[data_clean$algorithm == a], 0.25)),
  median = sapply(algorithms, function(a) median(data_clean$memoryUsedKB[data_clean$algorithm == a])),
  mean = sapply(algorithms, function(a) mean(data_clean$memoryUsedKB[data_clean$algorithm == a])),
  q3 = sapply(algorithms, function(a) quantile(data_clean$memoryUsedKB[data_clean$algorithm == a], 0.75)),
  max = sapply(algorithms, function(a) max(data_clean$memoryUsedKB[data_clean$algorithm == a]))
)
print(memory_summary)

# Processing time summary by algorithm
cat("\nProcessing Time Summary (μs):\n")
time_summary <- data.frame(
  algorithm = algorithms,
  min = sapply(algorithms, function(a) min(data_clean$processingTimeUs[data_clean$algorithm == a])),
  q1 = sapply(algorithms, function(a) quantile(data_clean$processingTimeUs[data_clean$algorithm == a], 0.25)),
  median = sapply(algorithms, function(a) median(data_clean$processingTimeUs[data_clean$algorithm == a])),
  mean = sapply(algorithms, function(a) mean(data_clean$processingTimeUs[data_clean$algorithm == a])),
  q3 = sapply(algorithms, function(a) quantile(data_clean$processingTimeUs[data_clean$algorithm == a], 0.75)),
  max = sapply(algorithms, function(a) max(data_clean$processingTimeUs[data_clean$algorithm == a]))
)
print(time_summary)

# Results by data size and algorithm
cat("\nResults by Data Size:\n")
sizes <- unique(data_clean$dataSize)
size_summary <- data.frame()
for (size in sizes) {
  for (algo in algorithms) {
    subset_data <- data_clean[data_clean$dataSize == size & data_clean$algorithm == algo, ]
    if (nrow(subset_data) > 0) {
      size_summary <- rbind(size_summary, data.frame(
        dataSize = size,
        algorithm = algo,
        count = nrow(subset_data),
        avg_memory_kb = mean(subset_data$memoryUsedKB),
        avg_time_us = mean(subset_data$processingTimeUs)
      ))
    }
  }
}
print(size_summary)

cat("\n")
cat(paste(rep("=", 50), collapse = ""))
cat("\nANALYSIS COMPLETE!\n")
cat(paste(rep("=", 50), collapse = ""))
cat("Generated files:\n")
cat("  - analysis/memory_usage_by_algorithm.png\n")
cat("  - analysis/memory_usage_by_algorithm_log.png\n")
cat("  - analysis/processing_time_by_algorithm.png\n")
cat("  - analysis/processing_time_by_algorithm_log.png\n")