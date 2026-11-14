# Malay voice prompts: review sheet

RoadPulse generates Bahasa Melayu instructions itself, because the routing server (Valhalla) has
no Malay. All Malay wording is in `app/src/main/java/com/roadpulse/app/nav/Narration.kt`
(`MalayNarrator`). **Please have a native speaker review this sheet** and note corrections next to
any line; wording changes only need edits in that one file.

Generated from two real routes (© OpenStreetMap contributors). Spoken at the turn; the advance
warning adds "Dalam N meter, …" in front.

Known differences from the English, to be addressed in later batches:
- The English sometimes chains the next step ("Turn right. Then enter the roundabout…"); the
  Malay does not yet (batch 7, merged prompts).
- On motorways the Malay names only the main road number (e.g. "E6"), not every route number.

Status messages: "Anda telah tiba." · "Mencari laluan baharu." · "Isyarat GPS hilang. Teruskan
mengikut jalan." · "Tiada sambungan internet. Laluan semasa dikekalkan." · "Menyambung perjalanan
ke …" · banner: "Dalam 421 m", "Ikut laluan", "Luar talian: laluan semasa dikekalkan",
"Isyarat GPS lemah".

## Putrajaya (roundabouts, local streets)

| English (routing server) | Bahasa Melayu (RoadPulse) |
|---|---|
| Drive southeast. | Pandu ke arah tenggara. |
| Turn right. Then Enter the roundabout and take the 2nd exit onto Jalan P18 C1. | Belok kanan. |
| Enter the roundabout and take the 2nd exit onto Jalan P18 C1. | Masuk ke bulatan dan ambil jalan keluar kedua ke Jalan P18 C1. |
| Exit the roundabout onto Jalan P18 C1. | Keluar dari bulatan ke Jalan P18 C1. |
| Turn left onto Jalan P18 C. | Belok kiri ke Jalan P18 C. |
| Make a right U-turn at Jalan P18 to stay on Jalan P18 C. | Buat pusingan U ke Jalan P18 C. |
| Turn right onto Lebuh Bestari. | Belok kanan ke Lebuh Bestari. |
| Turn left onto Lebuh Perdana Selatan. | Belok kiri ke Lebuh Perdana Selatan. |
| Keep left to take Lebuh Perdana Barat. | Kekal di sebelah kiri untuk masuk Lebuh Perdana Barat. |
| Turn right onto Jalan Rajawali P9 B 1. Then Turn right onto Jalan P9B1/9. | Belok kanan ke Jalan Rajawali P9 B 1. |
| Turn right onto Jalan P9B1/9. | Belok kanan ke Jalan P9B1/9. |
| Turn left onto Jalan P9B1/10. | Belok kiri ke Jalan P9B1/10. |
| Turn left onto Jalan P9B1/11. | Belok kiri ke Jalan P9B1/11. |
| Your destination is on the right. | Destinasi anda di sebelah kanan. |

Advance warning example: "Dalam 450 meter, belok kanan."

## Kuala Lumpur to Seremban (motorway exits)

| English (routing server) | Bahasa Melayu (RoadPulse) |
|---|---|
| Drive east on Jalan Damansara. | Pandu ke arah timur di Jalan Damansara. |
| Keep right to take E37 toward E2, E26. | Kekal di sebelah kanan untuk masuk E37 menuju E2, E26. |
| Take the E37 exit toward Putrajaya, Cyberjaya. | Ambil susur keluar di sebelah kiri ke E37 menuju Putrajaya, Cyberjaya. |
| Keep left to take E20 toward Putrajaya, Cyberjaya. | Kekal di sebelah kiri untuk masuk E20 menuju Putrajaya, Cyberjaya. |
| Keep right to take E6. | Kekal di sebelah kanan untuk masuk E6. |
| Keep left to take E6, AH2 toward Johor Bahru,  Nilai. | Kekal di sebelah kiri untuk masuk E6 menuju Johor Bahru, Nilai. |
| Continue on E6, AH2. | Teruskan di E6. |
| Take exit 608 onto E6, 26 toward Kuala Lumpur International Airport, Sepang. | Ambil susur keluar 608 di sebelah kiri ke E6 menuju Kuala Lumpur International Airport, Sepang. |
| Keep left at the fork. | Kekal di sebelah kiri di persimpangan. |
| Turn right. | Belok kanan. |
| Enter the roundabout and take the 2nd exit. | Masuk ke bulatan dan ambil jalan keluar kedua. |
| Exit the roundabout. | Keluar dari bulatan. |
| Turn right. | Belok kanan. |
| Turn right. | Belok kanan. |
| Turn left. | Belok kiri. |
| Turn right. | Belok kanan. |
| Keep left at the fork. | Kekal di sebelah kiri di persimpangan. |
| Keep left at the fork. | Kekal di sebelah kiri di persimpangan. |
| Your destination is on the left. | Destinasi anda di sebelah kiri. |

Advance warning example: "Dalam 450 meter, kekal di sebelah kanan untuk masuk E37 menuju E2, E26."
