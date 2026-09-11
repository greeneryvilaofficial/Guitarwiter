# Taruh file-file ini persis di path yang sama di repo GitHub Anda

www/icon-192.png                                  -> timpa file yang lama
www/icon-512.png                                  -> timpa file yang lama
native-patches/app-icon/**                        -> folder BARU, commit apa adanya
native-patches/insert_icon.py                      -> file BARU
.github/workflows/android-build.yml               -> timpa yang lama (nambah 1 step baru)

Setelah ke-4 hal di atas di-push ke branch main, workflow "Build Android APK"
otomatis jalan, dan APK barunya akan punya ikon launcher dari foto Anda.
Ingat: uninstall dulu APK versi lama di HP sebelum install yang baru.
