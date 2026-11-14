# Arabic screens: review sheet

Every Arabic text on RoadPulse screens next to the English (`ArabicStrings` in
`app/src/main/java/com/roadpulse/app/i18n/Strings.kt`). Voice prompts are in `ARABIC_PROMPTS.md`.
Write corrections in the last column. Debug-only screens (simulation, field-test flags, laptop server) stay in English.

Regenerate: `gradlew :app:testDebugUnitTest --tests *StringsTest*` writes `app/build/arabic_screens.tsv`.

| # | Key | English | العربية (current) | OK? / better wording |
|---|---|---|---|---|
| 1 | AddStop | Add stop | أضف توقفًا | |
| 2 | AllowUnpaved | Allow unpaved roads | السماح بالطرق غير المعبدة | |
| 3 | ArriveBy | Arrive by | الوصول قبل | |
| 4 | Arrived | You have arrived | لقد وصلت | |
| 5 | AvoidFerries | Avoid ferries | تجنّب العبّارات | |
| 6 | AvoidFerriesShort | ferries | العبّارات | |
| 7 | AvoidHighways | Avoid highways | تجنّب الطرق السريعة | |
| 8 | AvoidHighwaysShort | highways | الطرق السريعة | |
| 9 | AvoidTolls | Avoid toll roads | تجنّب الطرق ذات الرسوم | |
| 10 | AvoidTollsShort | tolls | الرسوم | |
| 11 | Back | Back | رجوع | |
| 12 | Cancel | Cancel | إلغاء | |
| 13 | ChooseDestinationFirst | Choose a destination first, then add stops | اختر وجهة أولًا، ثم أضف التوقفات | |
| 14 | Clear | Clear | مسح | |
| 15 | ClearSavedPlaces | Clear saved places | مسح الأماكن المحفوظة | |
| 16 | Close | Close | إغلاق | |
| 17 | CouldNotLoadPlaces | Could not load places. Check your connection. | تعذّر تحميل الأماكن. تحقق من الاتصال. | |
| 18 | CouldNotSendReport | Could not send report. Check your connection and try again. | تعذّر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا. | |
| 19 | Destination | Destination | الوجهة | |
| 20 | Done | Done | تم | |
| 21 | DriveHere | Drive here | انطلق إلى هنا | |
| 22 | DroppedPin | Dropped pin | دبوس على الخريطة | |
| 23 | FlatMap | Flat map while driving (2D) | خريطة مسطحة أثناء القيادة (ثنائية الأبعاد) | |
| 24 | ForgetParking | Forget parking spot | انسَ مكان الركن | |
| 25 | Free | free | مجاني | |
| 26 | GoNow | Go now | انطلق الآن | |
| 27 | Hide | Hide | إخفاء | |
| 28 | Home | Home | المنزل | |
| 29 | HowLong | How long? | كم المدة؟ | |
| 30 | Kmh | km/h | كم/س | |
| 31 | Language | Language | اللغة | |
| 32 | LeaveLater | Leave later | انطلق لاحقًا | |
| 33 | LeaveRemindersChannel | Time to leave reminders | تذكيرات وقت الانطلاق | |
| 34 | LocationUnavailable | Location unavailable | الموقع غير متاح | |
| 35 | LocationUnavailableReport | Location unavailable, cannot place report | الموقع غير متاح، لا يمكن إضافة البلاغ | |
| 36 | MapColours | Map colours | ألوان الخريطة | |
| 37 | MapView | Map view | عرض الخريطة | |
| 38 | MusicControls | Music controls while driving | التحكم بالموسيقى أثناء القيادة | |
| 39 | MusicControlsHelp | Play, pause and skip in Spotify, YouTube Music and other apps from the driving screen. | شغّل وأوقف وتنقّل بين المقاطع في Spotify وYouTube Music وغيرها من شاشة القيادة. | |
| 40 | MusicNeedsAccess | Android asks for notification access for this. RoadPulse uses it only to see which music is playing, not to read your notifications. | سيطلب أندرويد إذن الوصول إلى الإشعارات. يستخدمه RoadPulse فقط لمعرفة الموسيقى المشغّلة، ولا يقرأ إشعاراتك. | |
| 41 | MuteVoice | Mute voice | كتم الصوت | |
| 42 | MuteVoiceSetting | Mute voice | كتم الصوت | |
| 43 | Navigation | Navigation | الملاحة | |
| 44 | NextTrack | Next track | المقطع التالي | |
| 45 | NorthUp | North always up | الشمال للأعلى دائمًا | |
| 46 | NotThere | Not there | غير موجود | |
| 47 | NotificationChannel | Shown while RoadPulse is guiding a drive | يظهر أثناء إرشاد RoadPulse لرحلتك | |
| 48 | NotificationStop | Stop | إيقاف | |
| 49 | Overview | Overview | عرض المسار | |
| 50 | Paid | paid | مدفوع | |
| 51 | ParkedHere | Parked here | ركنت هنا | |
| 52 | ParkingSpotSaved | Parking spot saved | تم حفظ مكان الركن | |
| 53 | Pause | Pause | إيقاف مؤقت | |
| 54 | PlaceNameHint | Name, e.g. Gym | الاسم، مثلًا النادي | |
| 55 | PlanDrive | Plan this drive | خطط لهذه الرحلة | |
| 56 | PlannedDrives | Planned drives | رحلات مخطط لها | |
| 57 | Play | Play | تشغيل | |
| 58 | PreviousTrack | Previous track | المقطع السابق | |
| 59 | Privacy | Privacy | الخصوصية | |
| 60 | PrivacyText | Home, Work and recent places are stored only on this phone. | يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط. | |
| 61 | RemoveStop | Remove stop | إزالة التوقف | |
| 62 | Report | Report | بلّغ | |
| 63 | ReportAbuse | Report abuse | الإبلاغ عن إساءة | |
| 64 | Route | Route | المسار | |
| 65 | Save | Save | حفظ | |
| 66 | SavePlace | Save place | حفظ المكان | |
| 67 | SavedPlacesCleared | Saved places cleared | تم مسح الأماكن المحفوظة | |
| 68 | SearchByVoice | Search by voice | البحث بالصوت | |
| 69 | Send | Send | إرسال | |
| 70 | SetAddress | Set address | عيّن العنوان | |
| 71 | SetReminder | Remind me | ذكّرني | |
| 72 | Settings | Settings | الإعدادات | |
| 73 | SettingsButton | Voice guidance settings | الإعدادات | |
| 74 | Show | Show | عرض | |
| 75 | ShowCompass | Show compass | إظهار البوصلة | |
| 76 | ShowMyLocation | Show my location | أظهر موقعي | |
| 77 | ShowSpeedLimit | Show speed limit | إظهار حد السرعة | |
| 78 | ShowSpeedometer | Show speedometer | إظهار عدّاد السرعة | |
| 79 | SimulateDrive | Simulate a drive | محاكاة رحلة | |
| 80 | Skip | Skip | تخطٍّ | |
| 81 | SoundWhenSpeeding | Sound when speeding | صوت تنبيه عند تجاوز السرعة | |
| 82 | Speed | Speed | السرعة | |
| 83 | StillThere | Still there | ما زال موجودًا | |
| 84 | StopNavigation | Stop navigation | إيقاف الملاحة | |
| 85 | TagFastest | Fastest | الأسرع | |
| 86 | TagNoTolls | No tolls | بلا رسوم | |
| 87 | TagShortest | Shortest | الأقصر | |
| 88 | Thanks | Thanks! | شكرًا! | |
| 89 | TipAddStop | Tip: press and hold the map to add a stop | تلميح: اضغط مطولًا على الخريطة لإضافة توقف | |
| 90 | Today | Today | اليوم | |
| 91 | TollPriceUnknown | Toll: price unknown | رسوم: السعر غير معروف | |
| 92 | TollRoad | Toll road | طريق برسوم | |
| 93 | Tomorrow | Tomorrow | غدًا | |
| 94 | TooLate | Too late to arrive by then: choose a later time. | فات الأوان للوصول في هذا الوقت: اختر وقتًا لاحقًا. | |
| 95 | UnmuteVoice | Unmute voice | إلغاء كتم الصوت | |
| 96 | UnpavedAllowed | unpaved roads allowed | مع السماح بالطرق غير المعبدة | |
| 97 | WaitingForLocation | Waiting for your location. Enable GPS and try again. | بانتظار موقعك. فعّل GPS وحاول مجددًا. | |
| 98 | WarnOverLimitBy | Warn when over the limit by | التنبيه عند تجاوز الحد بمقدار | |
| 99 | WhatsWrongWithReport | What's wrong with this report? | ما المشكلة في هذا البلاغ؟ | |
| 100 | WhereTo | Where to? | إلى أين؟ | |
| 101 | Work | Work | العمل | |
| 102 | YourHomeAddress | Your home address | عنوان منزلك | |
| 103 | YourReport | Your report. Other drivers can confirm it. | بلاغك. يمكن للسائقين الآخرين تأكيده. | |
| 104 | YourWorkAddress | Your work address | عنوان عملك | |
| 105 | category FUEL | Fuel | وقود | |
| 106 | category PARKING | Parking | مواقف | |
| 107 | category FOOD | Food | طعام | |
| 108 | category EV_CHARGING | EV charging | شحن كهربائي | |
| 109 | category REST_AREA | R&R | استراحة | |
| 110 | category SPEED_CAMERA | Speed camera | كاميرا سرعة | |
| 111 | category SCHOOL_ZONE | School | مدرسة | |
| 112 | report POLICE | Police | شرطة | |
| 113 | report CRASH | Crash | حادث | |
| 114 | report HAZARD | Hazard | خطر | |
| 115 | report TRAFFIC_JAM | Traffic jam | ازدحام | |
| 116 | report ROAD_CLOSED | Road closed | طريق مغلق | |
| 117 | detail VISIBLE | Visible | ظاهرة | |
| 118 | detail HIDDEN | Hidden | مخفية | |
| 119 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 120 | detail MINOR | Minor | بسيط | |
| 121 | detail MAJOR | Major | كبير | |
| 122 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 123 | detail OBJECT_ON_ROAD | Object on road | جسم على الطريق | |
| 124 | detail POTHOLE | Pothole | حفرة | |
| 125 | detail STOPPED_VEHICLE | Stopped vehicle | مركبة متوقفة | |
| 126 | detail ANIMAL | Animal | حيوان | |
| 127 | detail FLOOD | Flood | فيضان | |
| 128 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 129 | detail MODERATE | Moderate | متوسط | |
| 130 | detail HEAVY | Heavy | شديد | |
| 131 | detail STANDSTILL | Standstill | متوقف تمامًا | |
| 132 | detail FULL | Fully closed | مغلق كليًا | |
| 133 | detail LANE_BLOCKED | Lane blocked | مسار مغلق | |
| 134 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 135 | vehicle CAR | Car | سيارة | |
| 136 | vehicle MOTORCYCLE | Motorcycle | دراجة نارية | |
| 137 | vehicle TAXI | Taxi | تاكسي | |
| 138 | flag FAKE | Fake report | بلاغ كاذب | |
| 139 | flag SPAM | Spam | رسائل مزعجة | |
| 140 | flag OFFENSIVE | Offensive | مسيء | |
| 141 | flag WRONG_LOCATION | Wrong location | موقع خاطئ | |
| 142 | map colours AUTO | Automatic (dark after sunset) | تلقائي (داكن بعد الغروب) | |
| 143 | map colours LIGHT | Always light | فاتح دائمًا | |
| 144 | map colours DARK | Always dark | داكن دائمًا | |
| 145 | limit ALWAYS | Always | دائمًا | |
| 146 | limit WHEN_SPEEDING | Only when speeding | عند تجاوز السرعة فقط | |
| 147 | limit NEVER | Never | أبدًا | |
| 148 | parked | Your car · parked 25 min ago | سيارتك · رُكنت منذ 25 د | |
| 149 | stop | Stop 1: KLCC | توقف 1: KLCC | |
| 150 | car parks | 3 car parks near your destination | 3 مواقف سيارات قرب وجهتك | |
| 151 | toll | Toll ~RM 6.80 | رسوم تقريبًا RM 6.80 | |
| 152 | toll partial | Toll RM 5.00 + ? | رسوم RM 5.00 أو أكثر | |
| 153 | summary | Motorcycle · avoiding tolls, highways | دراجة نارية · مع تجنب الرسوم والطرق السريعة | |
| 154 | reported | Police reported. Thanks! | تم الإبلاغ عن شرطة. شكرًا! | |
| 155 | nearby | Fuel nearby | وقود بالقرب منك | |
| 156 | none within | No fuel within 10 km | لا يوجد وقود ضمن 10 كم | |
| 157 | closure 3h | 3 hours | 3 ساعات | |
| 158 | notification | In 350 m: Navigating to KLCC | بعد 350 م: الملاحة إلى KLCC | |
