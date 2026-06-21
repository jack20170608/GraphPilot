#!/bin/bash
set -e

# GraphPilot Build Script
# 构建后端项目

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "==== GraphPilot Build Script ===="
echo "Project root: $PROJECT_ROOT"
echo ""

# 默认值
SKIP_TESTS=false
PROFILE="default"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--skip-tests] [--profile <profile>]"
            exit 1
            ;;
    esac
done

cd "$PROJECT_ROOT"

echo "Java version:"
java -version
echo ""

echo "Maven version:"
mvn -version
echo ""

# 构建后端
echo "Building backend..."
if [ "$SKIP_TESTS" = true ]; then
    echo "Skipping tests..."
    mvn -f backend/pom.xml clean package -DskipTests -P"$PROFILE"
else
    mvn -f backend/pom.xml clean package -P"$PROFILE"
fi

echo ""
echo "==== Build Complete ===="
echo "Artifacts location: backend/*/target/"