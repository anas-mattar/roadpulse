# Arabic screens: review sheet

Every Arabic text on RoadPulse screens next to the English (`ArabicStrings` in
`app/src/main/java/com/roadpulse/app/i18n/Strings.kt`). Voice prompts are in `ARABIC_PROMPTS.md`.
Write corrections in the last column. Debug-only screens (simulation, field-test flags, laptop server) stay in English.

Regenerate: `gradlew :app:testDebugUnitTest --tests *StringsTest*` writes `app/build/arabic_screens.tsv`.

| # | Key | English | العربية (current) | OK? / better wording |
|---|---|---|---|---|
| 1 | AddStop | Add stop | أضف توقفًا | |
| 2 | AllowUnpaved | Allow unpaved roads | السماح بالطرق غير المعبدة | |
| 3 | Arrived | You have arrived | لقد وصلت | |
| 4 | AvoidFerries | Avoid ferries | تجنّب العبّارات | |
| 5 | AvoidFerriesShort | ferries | العبّارات | |
| 6 | AvoidHighways | Avoid highways | تجنّب الطرق السريعة | |
| 7 | AvoidHighwaysShort | highways | الطرق السريعة | |
| 8 | AvoidTolls | Avoid toll roads | تجنّب الطرق ذات الرسوم | |
| 9 | AvoidTollsShort | tolls | الرسوم | |
| 10 | Back | Back | رجوع | |
| 11 | Cancel | Cancel | إلغاء | |
| 12 | ChooseDestinationFirst | Choose a destination first, then add stops | اختر وجهة أولًا، ثم أضف التوقفات | |
| 13 | Clear | Clear | مسح | |
| 14 | ClearSavedPlaces | Clear saved places | مسح الأماكن المحفوظة | |
| 15 | Close | Close | إغلاق | |
| 16 | CouldNotLoadPlaces | Could not load places. Check your connection. | تعذّر تحميل الأماكن. تحقق من الاتصال. | |
| 17 | CouldNotSendReport | Could not send report. Check your connection and try again. | تعذّر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا. | |
| 18 | Destination | Destination | الوجهة | |
| 19 | Done | Done | تم | |
| 20 | DriveHere | Drive here | انطلق إلى هنا | |
| 21 | DroppedPin | Dropped pin | دبوس على الخريطة | |
| 22 | FlatMap | Flat map while driving (2D) | خريطة مسطحة أثناء القيادة (ثنائية الأبعاد) | |
| 23 | ForgetParking | Forget parking spot | انسَ مكان الركن | |
| 24 | Free | free | مجاني | |
| 25 | GoNow | Go now | انطلق الآن | |
| 26 | Hide | Hide | إخفاء | |
| 27 | Home | Home | المنزل | |
| 28 | HowLong | How long? | كم المدة؟ | |
| 29 | Kmh | km/h | كم/س | |
| 30 | Language | Language | اللغة | |
| 31 | LocationUnavailable | Location unavailable | الموقع غير متاح | |
| 32 | LocationUnavailableReport | Location unavailable, cannot place report | الموقع غير متاح، لا يمكن إضافة البلاغ | |
| 33 | MapColours | Map colours | ألوان الخريطة | |
| 34 | MapView | Map view | عرض الخريطة | |
| 35 | MuteVoice | Mute voice | كتم الصوت | |
| 36 | MuteVoiceSetting | Mute voice | كتم الصوت | |
| 37 | Navigation | Navigation | الملاحة | |
| 38 | NorthUp | North always up | الشمال للأعلى دائمًا | |
| 39 | NotThere | Not there | غير موجود | |
| 40 | NotificationChannel | Shown while RoadPulse is guiding a drive | يظهر أثناء إرشاد RoadPulse لرحلتك | |
| 41 | NotificationStop | Stop | إيقاف | |
| 42 | Paid | paid | مدفوع | |
| 43 | ParkedHere | Parked here | ركنت هنا | |
| 44 | ParkingSpotSaved | Parking spot saved | تم حفظ مكان الركن | |
| 45 | PlaceNameHint | Name, e.g. Gym | الاسم، مثلًا النادي | |
| 46 | Privacy | Privacy | الخصوصية | |
| 47 | PrivacyText | Home, Work and recent places are stored only on this phone. | يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط. | |
| 48 | RemoveStop | Remove stop | إزالة التوقف | |
| 49 | Report | Report | بلّغ | |
| 50 | ReportAbuse | Report abuse | الإبلاغ عن إساءة | |
| 51 | Route | Route | المسار | |
| 52 | Save | Save | حفظ | |
| 53 | SavePlace | Save place | حفظ المكان | |
| 54 | SavedPlacesCleared | Saved places cleared | تم مسح الأماكن المحفوظة | |
| 55 | SearchByVoice | Search by voice | البحث بالصوت | |
| 56 | Send | Send | إرسال | |
| 57 | SetAddress | Set address | عيّن العنوان | |
| 58 | Settings | Settings | الإعدادات | |
| 59 | SettingsButton | Voice guidance settings | الإعدادات | |
| 60 | Show | Show | عرض | |
| 61 | ShowCompass | Show compass | إظهار البوصلة | |
| 62 | ShowMyLocation | Show my location | أظهر موقعي | |
| 63 | ShowSpeedLimit | Show speed limit | إظهار حد السرعة | |
| 64 | ShowSpeedometer | Show speedometer | إظهار عدّاد السرعة | |
| 65 | SimulateDrive | Simulate a drive | محاكاة رحلة | |
| 66 | Skip | Skip | تخطٍّ | |
| 67 | SoundWhenSpeeding | Sound when speeding | صوت تنبيه عند تجاوز السرعة | |
| 68 | Speed | Speed | السرعة | |
| 69 | StillThere | Still there | ما زال موجودًا | |
| 70 | StopNavigation | Stop navigation | إيقاف الملاحة | |
| 71 | TagFastest | Fastest | الأسرع | |
| 72 | TagNoTolls | No tolls | بلا رسوم | |
| 73 | TagShortest | Shortest | الأقصر | |
| 74 | Thanks | Thanks! | شكرًا! | |
| 75 | TipAddStop | Tip: press and hold the map to add a stop | تلميح: اضغط مطولًا على الخريطة لإضافة توقف | |
| 76 | TollPriceUnknown | Toll: price unknown | رسوم: السعر غير معروف | |
| 77 | TollRoad | Toll road | طريق برسوم | |
| 78 | UnmuteVoice | Unmute voice | إلغاء كتم الصوت | |
| 79 | UnpavedAllowed | unpaved roads allowed | مع السماح بالطرق غير المعبدة | |
| 80 | WaitingForLocation | Waiting for your location. Enable GPS and try again. | بانتظار موقعك. فعّل GPS وحاول مجددًا. | |
| 81 | WarnOverLimitBy | Warn when over the limit by | التنبيه عند تجاوز الحد بمقدار | |
| 82 | WhatsWrongWithReport | What's wrong with this report? | ما المشكلة في هذا البلاغ؟ | |
| 83 | WhereTo | Where to? | إلى أين؟ | |
| 84 | Work | Work | العمل | |
| 85 | YourHomeAddress | Your home address | عنوان منزلك | |
| 86 | YourReport | Your report. Other drivers can confirm it. | بلاغك. يمكن للسائقين الآخرين تأكيده. | |
| 87 | YourWorkAddress | Your work address | عنوان عملك | |
| 88 | category FUEL | Fuel | وقود | |
| 89 | category PARKING | Parking | مواقف | |
| 90 | category FOOD | Food | طعام | |
| 91 | category EV_CHARGING | EV charging | شحن كهربائي | |
| 92 | category REST_AREA | R&R | استراحة | |
| 93 | category SPEED_CAMERA | Speed camera | كاميرا سرعة | |
| 94 | report POLICE | Police | شرطة | |
| 95 | report CRASH | Crash | حادث | |
| 96 | report HAZARD | Hazard | خطر | |
| 97 | report TRAFFIC_JAM | Traffic jam | ازدحام | |
| 98 | report ROAD_CLOSED | Road closed | طريق مغلق | |
| 99 | detail VISIBLE | Visible | ظاهرة | |
| 100 | detail HIDDEN | Hidden | مخفية | |
| 101 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 102 | detail MINOR | Minor | بسيط | |
| 103 | detail MAJOR | Major | كبير | |
| 104 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 105 | detail OBJECT_ON_ROAD | Object on road | جسم على الطريق | |
| 106 | detail POTHOLE | Pothole | حفرة | |
| 107 | detail STOPPED_VEHICLE | Stopped vehicle | مركبة متوقفة | |
| 108 | detail ANIMAL | Animal | حيوان | |
| 109 | detail FLOOD | Flood | فيضان | |
| 110 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 111 | detail MODERATE | Moderate | متوسط | |
| 112 | detail HEAVY | Heavy | شديد | |
| 113 | detail STANDSTILL | Standstill | متوقف تمامًا | |
| 114 | detail FULL | Fully closed | مغلق كليًا | |
| 115 | detail LANE_BLOCKED | Lane blocked | مسار مغلق | |
| 116 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 117 | vehicle CAR | Car | سيارة | |
| 118 | vehicle MOTORCYCLE | Motorcycle | دراجة نارية | |
| 119 | vehicle TAXI | Taxi | تاكسي | |
| 120 | flag FAKE | Fake report | بلاغ كاذب | |
| 121 | flag SPAM | Spam | رسائل مزعجة | |
| 122 | flag OFFENSIVE | Offensive | مسيء | |
| 123 | flag WRONG_LOCATION | Wrong location | موقع خاطئ | |
| 124 | map colours AUTO | Automatic (dark after sunset) | تلقائي (داكن بعد الغروب) | |
| 125 | map colours LIGHT | Always light | فاتح دائمًا | |
| 126 | map colours DARK | Always dark | داكن دائمًا | |
| 127 | limit ALWAYS | Always | دائمًا | |
| 128 | limit WHEN_SPEEDING | Only when speeding | عند تجاوز السرعة فقط | |
| 129 | limit NEVER | Never | أبدًا | |
| 130 | parked | Your car · parked 25 min ago | سيارتك · رُكنت منذ 25 د | |
| 131 | stop | Stop 1: KLCC | توقف 1: KLCC | |
| 132 | car parks | 3 car parks near your destination | 3 مواقف سيارات قرب وجهتك | |
| 133 | toll | Toll ~RM 6.80 | رسوم تقريبًا RM 6.80 | |
| 134 | toll partial | Toll RM 5.00 + ? | رسوم RM 5.00 أو أكثر | |
| 135 | summary | Motorcycle · avoiding tolls, highways | دراجة نارية · مع تجنب الرسوم والطرق السريعة | |
| 136 | reported | Police reported. Thanks! | تم الإبلاغ عن شرطة. شكرًا! | |
| 137 | nearby | Fuel nearby | وقود بالقرب منك | |
| 138 | none within | No fuel within 10 km | لا يوجد وقود ضمن 10 كم | |
| 139 | closure 3h | 3 hours | 3 ساعات | |
| 140 | notification | In 350 m: Navigating to KLCC | بعد 350 م: الملاحة إلى KLCC | |
