# FlipMate

MEXC Futures için SIM-first, kullanıcı onaylı native Android işlem asistanı. Backend, Python ve WebView yoktur.

## Güvenlik
API bilgileri Android Keystore tabanlı encrypted storage ile saklanır. Withdrawal iznini kesinlikle açmayın. SIM modunda private emir endpointlerine istek gönderilmez. REAL moda geçiş credential doğrulaması ve kullanıcı onayı gerektirir.

## GitHub Actions
Actions sekmesinden **Android Debug APK** workflow'unu çalıştırın; tamamlanınca `flipmate-debug-apk` artifact'ini indirin. Bu proje üzerinde build veya canlı emir çalıştırılmamıştır.
