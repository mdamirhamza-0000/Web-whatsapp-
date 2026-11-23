# Web WhatsApp (personal use)

`https://web.whatsapp.com` ko ek Android app me kholta hai. Upar right corner me ek toggle icon hai jisse **Desktop mode** ON/OFF hota hai.

## Desktop mode kaise kaam karta hai

- Icon **phone** = desktop mode OFF
- Icon **hara monitor** = desktop mode ON
- Tap karne par browser ID (User-Agent) badal jata hai aur page reload hota hai.
- Aapka choice yaad rakha jata hai.

WhatsApp Web mobile browser par "not supported" dikhata hai, isliye QR scan karne ke liye pehle desktop mode ON karein.

## APK kaise milega (Android Studio ki zaroorat nahi)

1. GitHub par is repo ke **Actions** tab me jayein.
2. Latest **Build APK** run kholein.
3. Neeche **Artifacts** se `web-whatsapp-apk` download karein, zip kholein.
4. `app-debug.apk` ko phone me install karein (unknown sources allow karna padega).

Agar run na dikhe to **Actions → Build APK → Run workflow** dabayein.

## Limits

- Web notifications WebView me kaam nahi karte, to naye message ka notification nahi aayega.
- `blob:` wali files (WhatsApp Web ke kuch downloads) download nahi hongi.
- Ye unofficial wrapper hai, WhatsApp/Meta se judha nahi hai.
