# Development Setup

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Firebase project (for sync features)

## Getting Started

```bash
# Clone the repository
git clone <repo-url>
cd vault-ledger

# Create local properties
cp local.properties.example local.properties
# Add sdk.dir and Firebase config

# Sync project
./gradlew build
```

## Firebase Setup

1. Create a Firebase project in the Firebase Console
2. Enable Firestore and Authentication (Email + Google)
3. Download `google-services.json` and place it in `app/`
4. Enable Firestore indexes for the `transactions` collection on `vaultId` and `lastModified`

## Run

```bash
# Debug build
./gradlew :app:assembleDebug

# Run tests
./gradlew test

# Run UI tests
./gradlew connectedCheck
```
