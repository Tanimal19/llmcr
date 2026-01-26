#!/usr/bin/env python3
"""
Log Parser for extracting completion times from application logs.

Example log format:
[2026-01-23 15:36:24] [INFO] FaissVectorStore: Similarity search completed in 1578ms
[2026-01-23 15:36:24] [INFO] RAGService: Retrieval completed in 3168ms
[2026-01-23 15:37:19] [INFO] RAGService: Generation completed in 55395ms
"""

import re
import argparse
from collections import defaultdict
from pathlib import Path
import statistics


class LogParser:
    """Parser for extracting completion times from log files."""

    def __init__(self, log_file):
        self.log_file = Path(log_file)
        self.completion_pattern = re.compile(
            r"\[([\d\-\s:]+)\]\s+\[(\w+)\]\s+([\w\.]+):\s+(.*?)\s+completed in\s+(\d+)ms"
        )
        self.completions = defaultdict(list)

    def parse(self):
        """Parse the log file and extract completion times."""
        if not self.log_file.exists():
            raise FileNotFoundError(f"Log file not found: {self.log_file}")

        with open(self.log_file, "r", encoding="utf-8") as f:
            for line in f:
                match = self.completion_pattern.search(line)
                if match:
                    timestamp, level, service, operation, duration = match.groups()
                    key = f"{service}:{operation}"
                    self.completions[key].append(
                        {
                            "timestamp": timestamp,
                            "level": level,
                            "service": service,
                            "operation": operation,
                            "duration_ms": int(duration),
                        }
                    )

        return self.completions

    def get_statistics(self):
        """Calculate statistics for each operation type."""
        stats = {}

        for key, entries in self.completions.items():
            durations = [e["duration_ms"] for e in entries]

            stats[key] = {
                "count": len(durations),
                "min_ms": min(durations),
                "max_ms": max(durations),
                "avg_ms": statistics.mean(durations),
                "median_ms": statistics.median(durations),
            }

            if len(durations) > 1:
                stats[key]["stdev_ms"] = statistics.stdev(durations)
            else:
                stats[key]["stdev_ms"] = 0.0

        return stats

    def print_report(self):
        """Print a formatted report of completion times."""
        if not self.completions:
            print("No completion times found in the log file.")
            return

        print(f"\n{'='*80}")
        print(f"Log Parser Report: {self.log_file.name}")
        print(f"{'='*80}\n")

        stats = self.get_statistics()

        for key, stat in sorted(stats.items()):
            print(f"Operation: {key}")
            print(f"  Count:    {stat['count']}")
            print(f"  Min:      {stat['min_ms']:,} ms")
            print(f"  Max:      {stat['max_ms']:,} ms")
            print(f"  Average:  {stat['avg_ms']:,.2f} ms")
            print(f"  Median:   {stat['median_ms']:,.2f} ms")
            if stat["count"] > 1:
                print(f"  Std Dev:  {stat['stdev_ms']:,.2f} ms")
            print()

        print(f"{'='*80}\n")

    def export_csv(self, output_file):
        """Export all completion times to a CSV file."""
        with open(output_file, "w", encoding="utf-8") as f:
            f.write("timestamp,service,operation,duration_ms\n")
            for entries in self.completions.values():
                for entry in entries:
                    f.write(
                        f"{entry['timestamp']},{entry['service']},"
                        f"{entry['operation']},{entry['duration_ms']}\n"
                    )
        print(f"Exported to: {output_file}")


def main():
    parser = argparse.ArgumentParser(
        description="Parse completion times from application log files"
    )
    parser.add_argument("log_file", help="Path to the log file to parse")
    parser.add_argument(
        "--csv", help="Export results to CSV file", metavar="OUTPUT_FILE"
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress report output (useful with --csv)",
    )

    args = parser.parse_args()

    log_parser = LogParser(args.log_file)
    log_parser.parse()

    if not args.quiet:
        log_parser.print_report()

    if args.csv:
        log_parser.export_csv(args.csv)


if __name__ == "__main__":
    main()
