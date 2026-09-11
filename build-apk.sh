#!/bin/bash

# GuitarWiter APK Build Script
# This script automates the entire APK building process

set -e  # Exit on any error

echo "🎸 GuitarWiter - APK Build Script"
echo "=================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed. Please install Node.js first."
    exit 1
fi
print_status "Node.js found: $(node --version)"

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    print_error "npm is not installed. Please install npm first."
    exit 1
fi
print_status "npm found: $(npm --version)"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    print_error "Java is not installed. Please install Java JDK 17+ first."
    exit 1
fi
print_status "Java found: $(java -version 2>&1 | head -1)"

# Check if Android SDK is installed
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    print_warning "ANDROID_SDK_ROOT or ANDROID_HOME not set. Gradle might fail."
    print_warning "Please set ANDROID_SDK_ROOT environment variable."
fi

echo ""
echo "📦 Step 1: Installing dependencies..."
npm ci
print_status "Dependencies installed"

echo ""
echo "🏗️  Step 2: Building web assets..."
npm run build
print_status "Web assets built successfully"

echo ""
echo "⚡ Step 3: Checking/Initializing Capacitor..."
if [ ! -d "android" ]; then
    print_status "Initializing Capacitor..."
    npx cap init guitarwiter com.greeneryvilla.guitarwiter --web-dir dist
    print_status "Capacitor initialized"
    
    print_status "Adding Android platform..."
    npx cap add android
    print_status "Android platform added"
else
    print_status "Capacitor already initialized"
fi

echo ""
echo "🔄 Step 4: Syncing Capacitor..."
npx cap sync
print_status "Capacitor synced"

echo ""
echo "🔨 Step 5: Building APK..."
cd android

# Check if gradlew exists
if [ ! -f "gradlew" ]; then
    print_error "gradlew not found. Trying to regenerate..."
    gradle wrapper --gradle-version=8.0
fi

chmod +x gradlew

print_status "Building Debug APK..."
./gradlew assembleDebug --stacktrace

print_status "Building Release APK..."
./gradlew assembleRelease --stacktrace

cd ..

echo ""
echo "=================================="
print_status "APK Build Complete!"
echo ""
echo "📁 Output APK files:"
echo "  Debug:   android/app/build/outputs/apk/debug/app-debug.apk"
echo "  Release: android/app/build/outputs/apk/release/app-release.apk"
echo ""
echo "📱 To install on device:"
echo "  adb install android/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "🚀 Build successful!"
