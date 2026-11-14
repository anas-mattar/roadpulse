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
| 42 | Overview | Overview | عرض المسار | |
| 43 | Paid | paid | مدفوع | |
| 44 | ParkedHere | Parked here | ركنت هنا | |
| 45 | ParkingSpotSaved | Parking spot saved | تم حفظ مكان الركن | |
| 46 | PlaceNameHint | Name, e.g. Gym | الاسم، مثلًا النادي | |
| 47 | Privacy | Privacy | الخصوصية | |
| 48 | PrivacyText | Home, Work and recent places are stored only on this phone. | يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط. | |
| 49 | RemoveStop | Remove stop | إزالة التوقف | |
| 50 | Report | Report | بلّغ | |
| 51 | ReportAbuse | Report abuse | الإبلاغ عن إساءة | |
| 52 | Route | Route | المسار | |
| 53 | Save | Save | حفظ | |
| 54 | SavePlace | Save place | حفظ المكان | |
| 55 | SavedPlacesCleared | Saved places cleared | تم مسح الأماكن المحفوظة | |
| 56 | SearchByVoice | Search by voice | البحث بالصوت | |
| 57 | Send | Send | إرسال | |
| 58 | SetAddress | Set address | عيّن العنوان | |
| 59 | Settings | Settings | الإعدادات | |
| 60 | SettingsButton | Voice guidance settings | الإعدادات | |
| 61 | Show | Show | عرض | |
| 62 | ShowCompass | Show compass | إظهار البوصلة | |
| 63 | ShowMyLocation | Show my location | أظهر موقعي | |
| 64 | ShowSpeedLimit | Show speed limit | إظهار حد السرعة | |
| 65 | ShowSpeedometer | Show speedometer | إظهار عدّاد السرعة | |
| 66 | SimulateDrive | Simulate a drive | محاكاة رحلة | |
| 67 | Skip | Skip | تخطٍّ | |
| 68 | SoundWhenSpeeding | Sound when speeding | صوت تنبيه عند تجاوز السرعة | |
| 69 | Speed | Speed | السرعة | |
| 70 | StillThere | Still there | ما زال موجودًا | |
| 71 | StopNavigation | Stop navigation | إيقاف الملاحة | |
| 72 | TagFastest | Fastest | الأسرع | |
| 73 | TagNoTolls | No tolls | بلا رسوم | |
| 74 | TagShortest | Shortest | الأقصر | |
| 75 | Thanks | Thanks! | شكرًا! | |
| 76 | TipAddStop | Tip: press and hold the map to add a stop | تلميح: اضغط مطولًا على الخريطة لإضافة توقف | |
| 77 | TollPriceUnknown | Toll: price unknown | رسوم: السعر غير معروف | |
| 78 | TollRoad | Toll road | طريق برسوم | |
| 79 | UnmuteVoice | Unmute voice | إلغاء كتم الصوت | |
| 80 | UnpavedAllowed | unpaved roads allowed | مع السماح بالطرق غير المعبدة | |
| 81 | WaitingForLocation | Waiting for your location. Enable GPS and try again. | بانتظار موقعك. فعّل GPS وحاول مجددًا. | |
| 82 | WarnOverLimitBy | Warn when over the limit by | التنبيه عند تجاوز الحد بمقدار | |
| 83 | WhatsWrongWithReport | What's wrong with this report? | ما المشكلة في هذا البلاغ؟ | |
| 84 | WhereTo | Where to? | إلى أين؟ | |
| 85 | Work | Work | العمل | |
| 86 | YourHomeAddress | Your home address | عنوان منزلك | |
| 87 | YourReport | Your report. Other drivers can confirm it. | بلاغك. يمكن للسائقين الآخرين تأكيده. | |
| 88 | YourWorkAddress | Your work address | عنوان عملك | |
| 89 | category FUEL | Fuel | وقود | |
| 90 | category PARKING | Parking | مواقف | |
| 91 | category FOOD | Food | طعام | |
| 92 | category EV_CHARGING | EV charging | شحن كهربائي | |
| 93 | category REST_AREA | R&R | استراحة | |
| 94 | category SPEED_CAMERA | Speed camera | كاميرا سرعة | |
| 95 | category SCHOOL_ZONE | School | مدرسة | |
| 96 | report POLICE | Police | شرطة | |
| 97 | report CRASH | Crash | حادث | |
| 98 | report HAZARD | Hazard | خطر | |
| 99 | report TRAFFIC_JAM | Traffic jam | ازدحام | |
| 100 | report ROAD_CLOSED | Road closed | طريق مغلق | |
| 101 | detail VISIBLE | Visible | ظاهرة | |
| 102 | detail HIDDEN | Hidden | مخفية | |
| 103 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 104 | detail MINOR | Minor | بسيط | |
| 105 | detail MAJOR | Major | كبير | |
| 106 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 107 | detail OBJECT_ON_ROAD | Object on road | جسم على الطريق | |
| 108 | detail POTHOLE | Pothole | حفرة | |
| 109 | detail STOPPED_VEHICLE | Stopped vehicle | مركبة متوقفة | |
| 110 | detail ANIMAL | Animal | حيوان | |
| 111 | detail FLOOD | Flood | فيضان | |
| 112 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 113 | detail MODERATE | Moderate | متوسط | |
| 114 | detail HEAVY | Heavy | شديد | |
| 115 | detail STANDSTILL | Standstill | متوقف تمامًا | |
| 116 | detail FULL | Fully closed | مغلق كليًا | |
| 117 | detail LANE_BLOCKED | Lane blocked | مسار مغلق | |
| 118 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 119 | vehicle CAR | Car | سيارة | |
| 120 | vehicle MOTORCYCLE | Motorcycle | دراجة نارية | |
| 121 | vehicle TAXI | Taxi | تاكسي | |
| 122 | flag FAKE | Fake report | بلاغ كاذب | |
| 123 | flag SPAM | Spam | رسائل مزعجة | |
| 124 | flag OFFENSIVE | Offensive | مسيء | |
| 125 | flag WRONG_LOCATION | Wrong location | موقع خاطئ | |
| 126 | map colours AUTO | Automatic (dark after sunset) | تلقائي (داكن بعد الغروب) | |
| 127 | map colours LIGHT | Always light | فاتح دائمًا | |
| 128 | map colours DARK | Always dark | داكن دائمًا | |
| 129 | limit ALWAYS | Always | دائمًا | |
| 130 | limit WHEN_SPEEDING | Only when speeding | عند تجاوز السرعة فقط | |
| 131 | limit NEVER | Never | أبدًا | |
| 132 | parked | Your car · parked 25 min ago | سيارتك · رُكنت منذ 25 د | |
| 133 | stop | Stop 1: KLCC | توقف 1: KLCC | |
| 134 | car parks | 3 car parks near your destination | 3 مواقف سيارات قرب وجهتك | |
| 135 | toll | Toll ~RM 6.80 | رسوم تقريبًا RM 6.80 | |
| 136 | toll partial | Toll RM 5.00 + ? | رسوم RM 5.00 أو أكثر | |
| 137 | summary | Motorcycle · avoiding tolls, highways | دراجة نارية · مع تجنب الرسوم والطرق السريعة | |
| 138 | reported | Police reported. Thanks! | تم الإبلاغ عن شرطة. شكرًا! | |
| 139 | nearby | Fuel nearby | وقود بالقرب منك | |
| 140 | none within | No fuel within 10 km | لا يوجد وقود ضمن 10 كم | |
| 141 | closure 3h | 3 hours | 3 ساعات | |
| 142 | notification | In 350 m: Navigating to KLCC | بعد 350 م: الملاحة إلى KLCC | |
