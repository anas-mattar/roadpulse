# Arabic voice prompts: review sheet

Every Arabic phrase RoadPulse can say or show while driving, next to the English. The routing
server has no Arabic, so these sentences are built by the app (`ArabicNarrator` in
`app/src/main/java/com/roadpulse/app/nav/Narration.kt`). Modern Standard Arabic.

Please mark anything that sounds wrong and write the better wording in the last column. Street names
stay as on the map (Malay, Latin letters); the Arabic voice may read them with an accent.

Regenerate after changes: `gradlew :app:testDebugUnitTest --tests *MalayPromptSheetTest*` writes
`app/build/arabic_prompts.tsv`.

| # | Group | When it is used | English | العربية (current) | OK? / better wording |
|---|---|---|---|---|---|
| 1 | Start | Spoken at the turn and shown in the banner | Drive southeast. | انطلق باتجاه الجنوب الشرقي. | |
| 2 | Start | Spoken at the turn and shown in the banner | Drive east on Jalan Damansara. | انطلق باتجاه الشرق على Jalan Damansara. | |
| 3 | Arrive | Spoken at the turn and shown in the banner | You have arrived at your destination. | لقد وصلت إلى وجهتك. | |
| 4 | Arrive | Spoken at the turn and shown in the banner | Your destination is on the right. | وجهتك على اليمين. | |
| 5 | Arrive | Spoken at the turn and shown in the banner | Your destination is on the left. | وجهتك على اليسار. | |
| 6 | Continue | Spoken at the turn and shown in the banner | Jalan Duta becomes Jalan Ampang. | تابع إلى Jalan Ampang. | |
| 7 | Continue | Spoken at the turn and shown in the banner | Continue on E6. | تابع على E6. | |
| 8 | Turn | Spoken at the turn and shown in the banner | Bear right onto Jalan Tun Razak. | انعطف قليلًا إلى اليمين إلى Jalan Tun Razak. | |
| 9 | Turn | Spoken at the turn and shown in the banner | Turn right onto Jalan Ampang. | انعطف يمينًا إلى Jalan Ampang. | |
| 10 | Turn | Spoken at the turn and shown in the banner | Turn right. | انعطف يمينًا. | |
| 11 | Turn | Spoken at the turn and shown in the banner | Make a sharp right onto Jalan Pinang. | انعطف بحدة إلى اليمين إلى Jalan Pinang. | |
| 12 | U-turn | Spoken at the turn and shown in the banner | Make a right U-turn to stay on Jalan P18 C. | استدر للخلف إلى Jalan P18 C. | |
| 13 | Turn | Spoken at the turn and shown in the banner | Make a sharp left onto Jalan Raja Chulan. | انعطف بحدة إلى اليسار إلى Jalan Raja Chulan. | |
| 14 | Turn | Spoken at the turn and shown in the banner | Turn left onto Jalan P18 C. | انعطف يسارًا إلى Jalan P18 C. | |
| 15 | Turn | Spoken at the turn and shown in the banner | Bear left onto Jalan Sultan Ismail. | انعطف قليلًا إلى اليسار إلى Jalan Sultan Ismail. | |
| 16 | Ramp | Spoken at the turn and shown in the banner | Take the ramp straight ahead toward Putrajaya. | تابع مباشرة إلى المنحدر باتجاه Putrajaya. | |
| 17 | Ramp | Spoken at the turn and shown in the banner | Take the ramp on the right toward E2. | اسلك المنحدر على اليمين نحو E2. | |
| 18 | Ramp | Spoken at the turn and shown in the banner | Take the E37 ramp on the left toward Putrajaya, Cyberjaya. | اسلك المنحدر على اليسار نحو E37 باتجاه Putrajaya، Cyberjaya. | |
| 19 | Exit | Spoken at the turn and shown in the banner | Take exit 608 on the right toward Sepang. | اسلك المخرج رقم 608 على اليمين باتجاه Sepang. | |
| 20 | Exit | Spoken at the turn and shown in the banner | Take exit 608 onto E6 toward Kuala Lumpur International Airport, Sepang. | اسلك المخرج رقم 608 على اليسار نحو E6 باتجاه Kuala Lumpur International Airport، Sepang. | |
| 21 | Keep | Spoken at the turn and shown in the banner | Keep straight to take E2. | تابع مباشرة للدخول إلى E2. | |
| 22 | Keep | Spoken at the turn and shown in the banner | Keep right to take E37 toward E2, E26. | الزم اليمين للدخول إلى E37 باتجاه E2، E26. | |
| 23 | Keep | Spoken at the turn and shown in the banner | Keep left at the fork. | الزم اليسار عند التفرع. | |
| 24 | Keep | Spoken at the turn and shown in the banner | Keep left toward Kuantan, Hulu Kelang. | الزم اليسار باتجاه Kuantan، Hulu Kelang. | |
| 25 | Merge | Spoken at the turn and shown in the banner | Merge onto E6. | اندمج في E6. | |
| 26 | Merge | Spoken at the turn and shown in the banner | Merge. | اندمج مع حركة المرور. | |
| 27 | Roundabout | Spoken at the turn and shown in the banner | Enter the roundabout and take the 2nd exit onto Jalan P18 C1. | ادخل الدوار واسلك المخرج الثاني إلى Jalan P18 C1. | |
| 28 | Roundabout | Spoken at the turn and shown in the banner | Enter the roundabout and take the 3rd exit. | ادخل الدوار واسلك المخرج الثالث. | |
| 29 | Roundabout | Spoken at the turn and shown in the banner | Exit the roundabout onto Jalan P18 C1. | اخرج من الدوار إلى Jalan P18 C1. | |
| 30 | Ferry | Spoken at the turn and shown in the banner | Take the ferry. | استقل العبارة. | |
| 31 | Ferry | Spoken at the turn and shown in the banner | Leave the ferry onto Jalan Pelabuhan. | انزل من العبارة إلى Jalan Pelabuhan. | |
| 32 | Warning | Spoken before a turn | In 450 meters, turn right onto Jalan Ampang. | بعد 450 مترًا، انعطف يمينًا إلى Jalan Ampang. | |
| 33 | Two turns | Two turns less than 150 m apart, at the turn | Turn right onto Jalan Ampang, then turn left onto Jalan Pinang. | انعطف يمينًا إلى Jalan Ampang، ثم انعطف يسارًا إلى Jalan Pinang. | |
| 34 | Two turns | Two turns less than 150 m apart, before the turn | In 300 meters, turn right onto Jalan Ampang, then turn left onto Jalan Pinang. | بعد 300 متر، انعطف يمينًا إلى Jalan Ampang، ثم انعطف يسارًا إلى Jalan Pinang. | |
| 35 | Status | Spoken on arrival | You have arrived. | لقد وصلت. | |
| 36 | Status | Spoken when finding a new route | Rerouting. | جارٍ البحث عن مسار جديد. | |
| 37 | Status | Spoken when GPS is lost | GPS signal lost. Keep following the road. | فُقدت إشارة GPS. تابع السير على الطريق. | |
| 38 | Status | Spoken when there is no internet | No internet connection. Keeping your current route. | لا يوجد اتصال بالإنترنت. سيبقى المسار الحالي. | |
| 39 | Status | Spoken when the app reopens during a drive | Resuming your drive to M City. | متابعة الرحلة إلى M City. | |
| 40 | Screen | Distance in the banner | In 421 m | بعد 421 m | |
| 41 | Screen | Banner when there is no turn soon | Follow the route | اتبع المسار | |
| 42 | Screen | Label for the turn after next | Then | ثم | |
| 43 | Screen | Button that brings the map back to the car | Re-center | عودة إلى موقعي | |
| 44 | Screen | Status line: rerouting | Finding a new route… | جارٍ البحث عن مسار جديد… | |
| 45 | Screen | Status line: offline | Offline: keeping your current route | غير متصل: سيبقى المسار الحالي | |
| 46 | Screen | Status line: GPS lost | GPS signal lost: keep following the road | فُقدت إشارة GPS: تابع السير على الطريق | |
| 47 | Screen | Status line: weak GPS | Weak GPS signal | إشارة GPS ضعيفة | |
| 48 | Reports | Warning for a report on the route ahead | Police reported 700 meters ahead. | شرطة على بعد 700 متر أمامك. | |
| 49 | Reports | Warning for a report on the route ahead | Crash reported 700 meters ahead. | حادث على بعد 700 متر أمامك. | |
| 50 | Reports | Warning for a report on the route ahead | Hazard reported 700 meters ahead. | خطر على بعد 700 متر أمامك. | |
| 51 | Reports | Warning for a report on the route ahead | Traffic jam reported 700 meters ahead. | ازدحام مروري على بعد 700 متر أمامك. | |
| 52 | Reports | Warning for a report on the route ahead | Road closure reported 700 meters ahead. | إغلاق طريق على بعد 700 متر أمامك. | |
| 53 | Cameras | Warning for a fixed speed camera ahead | Speed camera in 500 meters. Limit 90. | كاميرا سرعة بعد 500 متر. الحد الأقصى 90. | |
| 54 | Cameras | Label of the speed camera hint | Speed camera | كاميرا سرعة | |
| 55 | Schools | Warning for a school on the route ahead | School zone in 300 meters. Watch for children. | منطقة مدرسة بعد 300 متر. انتبه للأطفال. | |
| 56 | Schools | Label of the school zone hint | School zone | منطقة مدرسة | |
| 57 | Traffic | A faster route around heavy traffic was found | Heavy traffic ahead. A faster route saves 6 minutes. | ازدحام شديد أمامك. يوجد مسار أسرع يوفّر 6 دقائق. | |
| 58 | Words | Roundabout exit number | exit 1 | الأول | |
| 59 | Words | Roundabout exit number | exit 2 | الثاني | |
| 60 | Words | Roundabout exit number | exit 3 | الثالث | |
| 61 | Words | Roundabout exit number | exit 4 | الرابع | |
| 62 | Words | Compass direction at the start | 0° | الشمال | |
| 63 | Words | Compass direction at the start | 45° | الشمال الشرقي | |
| 64 | Words | Compass direction at the start | 90° | الشرق | |
| 65 | Words | Compass direction at the start | 135° | الجنوب الشرقي | |
| 66 | Words | Compass direction at the start | 180° | الجنوب | |
| 67 | Words | Compass direction at the start | 225° | الجنوب الغربي | |
| 68 | Words | Compass direction at the start | 270° | الغرب | |
| 69 | Words | Compass direction at the start | 315° | الشمال الغربي | |
