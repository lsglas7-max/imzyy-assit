#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Safe helper to commit and push changes from Termux.
# Authentication is handled by GitHub CLI or Git credential manager; never embed a PAT.

if ! command -v git >/dev/null 2>&1; then
  echo "Git missing. Install with: pkg install git"
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Run this inside your cloned repository."
  exit 1
fi

branch=$(git branch --show-current)
if [[ -z "$branch" ]]; then
  echo "No current branch detected. Check out a branch first."
  exit 1
fi

echo "Repository: $(basename "$(git rev-parse --show-toplevel)")"
echo "Branch: $branch"
git status --short

read -r -p "Commit message: " message
if [[ -z "${message//[[:space:]]/}" ]]; then
  echo "Commit message cannot be empty."
  exit 1
fi

git add -A
if git diff --cached --quiet; then
  echo "Nothing staged to commit."
  exit 0
fi

git commit -m "$message"
echo "Pushing branch '$branch' to origin..."
git push -u origin "$branch"
echo "Done."
