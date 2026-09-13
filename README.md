# Nota Spese Android

Funzioni incluse:
- Nome, cognome, azienda e mese di riferimento
- Foto scontrino con fotocamera
- OCR automatico tramite Google ML Kit
- Lettura tentata di data, esercente, totale e valuta
- Correzione manuale prima del salvataggio
- CHF ed EUR separati
- Riepilogo mensile con totali
- PDF finale con elenco spese
- Foto degli scontrini allegate nel PDF
- Condivisione PDF

## Creare l'APK
1. Installa Android Studio.
2. Apri la cartella `NotaSpeseAndroid`.
3. Attendi la sincronizzazione Gradle.
4. Menu `Build` > `Build App Bundle(s) / APK(s)` > `Build APK(s)`.
5. Troverai normalmente l'APK in `app/build/outputs/apk/debug/app-debug.apk`.

Nota: l'OCR può richiedere correzioni manuali su scontrini poco leggibili.

## Compilazione automatica con GitHub Actions
Il progetto include `.github/workflows/build-apk.yml`.
Caricando il progetto su GitHub, il workflow `Build Android APK` compila automaticamente l'APK debug.
Al termine, scaricare l'artifact `NotaSpese-APK`, che contiene `app-debug.apk`.
