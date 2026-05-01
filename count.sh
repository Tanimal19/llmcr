#!/usr/bin/env bash

set -euo pipefail

target_path="${1:-.}"

if [[ ! -d "$target_path" ]]; then
	echo "Error: '$target_path' is not a directory or does not exist." >&2
	echo "Usage: $0 [path]" >&2
	exit 1
fi

# Count regular files by extension under the target path.
# Files without an extension are grouped as [no_ext].
find "$target_path" -type f -print0 |
	perl -0ne '
		chomp;
		$path = $_;
		next if $path eq "";
		$name = $path;
		$name =~ s{.*/}{};

		$ext = "[no_ext]";
		if ($name !~ /^\.[^.]+$/ && $name =~ /\.([^.]+)$/) {
			$ext = lc $1;
		}

		$size = -s $path;
		$size = 0 unless defined $size;

		$counts{$ext}++;
		$bytes{$ext} += $size;
		END {
			for my $e (keys %counts) {
				$kb = $bytes{$e} / 1024;
				printf "%s\t%d\t%.2f\n", $e, $counts{$e}, $kb;
			}
		}
	' |
	sort -k3,3nr -k1,1
