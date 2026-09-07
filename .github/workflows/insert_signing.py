import sys

path = sys.argv[1]

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

if "signingConfigs" in content:
    print("Signing config sudah ada, lewati.")
    sys.exit(0)

# 1) Sisipkan blok signingConfigs tepat setelah "android {"
signing_configs_block = """android {
    signingConfigs {
        release {
            storeFile file(System.getenv("KEYSTORE_PATH"))
            storePassword System.getenv("KEYSTORE_PASSWORD")
            keyAlias System.getenv("KEY_ALIAS")
            keyPassword System.getenv("KEY_PASSWORD")
        }
    }
"""
content = content.replace("android {", signing_configs_block, 1)

# 2) Tambahkan "signingConfig signingConfigs.release" di dalam blok release buildTypes
#    Anchor: baris "minifyEnabled" yang selalu ada bawaan template Capacitor.
content = content.replace(
    "minifyEnabled false",
    "signingConfig signingConfigs.release\n            minifyEnabled false",
    1
)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("Signing config berhasil ditambahkan ke build.gradle")
