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
| 12 | Badges | Badges | الأوسمة | |
| 13 | Cancel | Cancel | إلغاء | |
| 14 | ChooseDestinationFirst | Choose a destination first, then add stops | اختر وجهة أولًا، ثم أضف التوقفات | |
| 15 | Clear | Clear | مسح | |
| 16 | ClearSavedPlaces | Clear saved places | مسح الأماكن المحفوظة | |
| 17 | Close | Close | إغلاق | |
| 18 | CouldNotLoadPlaces | Could not load places. Check your connection. | تعذّر تحميل الأماكن. تحقق من الاتصال. | |
| 19 | CouldNotSendReport | Could not send report. Check your connection and try again. | تعذّر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا. | |
| 20 | Destination | Destination | الوجهة | |
| 21 | Done | Done | تم | |
| 22 | DriveHere | Drive here | انطلق إلى هنا | |
| 23 | DroppedPin | Dropped pin | دبوس على الخريطة | |
| 24 | FlatMap | Flat map while driving (2D) | خريطة مسطحة أثناء القيادة (ثنائية الأبعاد) | |
| 25 | ForgetParking | Forget parking spot | انسَ مكان الركن | |
| 26 | Free | free | مجاني | |
| 27 | GoNow | Go now | انطلق الآن | |
| 28 | HelpTraffic | Help with live traffic | ساعد في معرفة حركة المرور | |
| 29 | HelpTrafficText | When on, RoadPulse sends your speed and position in short anonymous pieces while you navigate: no account or device ID, nothing from the first and last 500 m of a trip, and the server keeps only average road speeds. Turn off at any time. | عند التفعيل، يرسل RoadPulse سرعتك وموقعك على شكل أجزاء قصيرة مجهولة الهوية أثناء الملاحة: بلا حساب أو معرّف جهاز، ولا شيء من أول وآخر 500 م من الرحلة، ويحتفظ الخادم بمتوسط سرعات الطرق فقط. يمكنك إيقافه في أي وقت. | |
| 30 | Hide | Hide | إخفاء | |
| 31 | Home | Home | المنزل | |
| 32 | HowLong | How long? | كم المدة؟ | |
| 33 | HowPointsWork | 10 points per report, 5 when another driver confirms it, 2 for checking someone's report. A report removed by moderators costs 20. | 10 نقاط لكل بلاغ، و5 عندما يؤكده سائق آخر، و2 عند التحقق من بلاغ غيرك. البلاغ الذي يحذفه المشرفون يخصم 20. | |
| 34 | Kmh | km/h | كم/س | |
| 35 | Language | Language | اللغة | |
| 36 | LeaveLater | Leave later | انطلق لاحقًا | |
| 37 | LeaveRemindersChannel | Time to leave reminders | تذكيرات وقت الانطلاق | |
| 38 | LocationUnavailable | Location unavailable | الموقع غير متاح | |
| 39 | LocationUnavailableReport | Location unavailable, cannot place report | الموقع غير متاح، لا يمكن إضافة البلاغ | |
| 40 | MapColours | Map colours | ألوان الخريطة | |
| 41 | MapView | Map view | عرض الخريطة | |
| 42 | MusicControls | Music controls while driving | التحكم بالموسيقى أثناء القيادة | |
| 43 | MusicControlsHelp | Play, pause and skip in Spotify, YouTube Music and other apps from the driving screen. | شغّل وأوقف وتنقّل بين المقاطع في Spotify وYouTube Music وغيرها من شاشة القيادة. | |
| 44 | MusicNeedsAccess | Android asks for notification access for this. RoadPulse uses it only to see which music is playing, not to read your notifications. | سيطلب أندرويد إذن الوصول إلى الإشعارات. يستخدمه RoadPulse فقط لمعرفة الموسيقى المشغّلة، ولا يقرأ إشعاراتك. | |
| 45 | MuteVoice | Mute voice | كتم الصوت | |
| 46 | MuteVoiceSetting | Mute voice | كتم الصوت | |
| 47 | Navigation | Navigation | الملاحة | |
| 48 | NextTrack | Next track | المقطع التالي | |
| 49 | Nickname | Nickname | الاسم المستعار | |
| 50 | NicknameHint | Optional. Shown on your profile; no real name needed. | اختياري. يظهر في ملفك الشخصي، ولا حاجة لاسمك الحقيقي. | |
| 51 | NorthUp | North always up | الشمال للأعلى دائمًا | |
| 52 | NotThere | Not there | غير موجود | |
| 53 | NotificationChannel | Shown while RoadPulse is guiding a drive | يظهر أثناء إرشاد RoadPulse لرحلتك | |
| 54 | NotificationStop | Stop | إيقاف | |
| 55 | Overview | Overview | عرض المسار | |
| 56 | Paid | paid | مدفوع | |
| 57 | ParkedHere | Parked here | ركنت هنا | |
| 58 | ParkingSpotSaved | Parking spot saved | تم حفظ مكان الركن | |
| 59 | Pause | Pause | إيقاف مؤقت | |
| 60 | PlaceNameHint | Name, e.g. Gym | الاسم، مثلًا النادي | |
| 61 | PlanDrive | Plan this drive | خطط لهذه الرحلة | |
| 62 | PlannedDrives | Planned drives | رحلات مخطط لها | |
| 63 | Play | Play | تشغيل | |
| 64 | PreviousTrack | Previous track | المقطع السابق | |
| 65 | Privacy | Privacy | الخصوصية | |
| 66 | PrivacyText | Home, Work and recent places are stored only on this phone. | يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط. | |
| 67 | Profile | Profile | الملف الشخصي | |
| 68 | ProfileNeedsServer | Your profile needs the RoadPulse server. Turn on "Use my laptop's server" in Settings. | يحتاج ملفك الشخصي إلى خادم RoadPulse. فعّل "Use my laptop's server" في الإعدادات. | |
| 69 | RemoveStop | Remove stop | إزالة التوقف | |
| 70 | Report | Report | بلّغ | |
| 71 | ReportAbuse | Report abuse | الإبلاغ عن إساءة | |
| 72 | Route | Route | المسار | |
| 73 | Save | Save | حفظ | |
| 74 | SavePlace | Save place | حفظ المكان | |
| 75 | SavedPlacesCleared | Saved places cleared | تم مسح الأماكن المحفوظة | |
| 76 | SearchByVoice | Search by voice | البحث بالصوت | |
| 77 | Send | Send | إرسال | |
| 78 | SetAddress | Set address | عيّن العنوان | |
| 79 | SetReminder | Remind me | ذكّرني | |
| 80 | Settings | Settings | الإعدادات | |
| 81 | SettingsButton | Voice guidance settings | الإعدادات | |
| 82 | ShareDrive | Share drive | شارك الرحلة | |
| 83 | ShareFailed | Could not share the drive. Check the connection to the server. | تعذّرت مشاركة الرحلة. تحقق من الاتصال بالخادم. | |
| 84 | SharingLive | Sharing live | المشاركة مباشرة | |
| 85 | Show | Show | عرض | |
| 86 | ShowCompass | Show compass | إظهار البوصلة | |
| 87 | ShowMyLocation | Show my location | أظهر موقعي | |
| 88 | ShowSpeedLimit | Show speed limit | إظهار حد السرعة | |
| 89 | ShowSpeedometer | Show speedometer | إظهار عدّاد السرعة | |
| 90 | SimulateDrive | Simulate a drive | محاكاة رحلة | |
| 91 | Skip | Skip | تخطٍّ | |
| 92 | SoundWhenSpeeding | Sound when speeding | صوت تنبيه عند تجاوز السرعة | |
| 93 | Speed | Speed | السرعة | |
| 94 | StatConfirmed | Confirmed by others | أكّدها آخرون | |
| 95 | StatReports | Reports | البلاغات | |
| 96 | StatVotes | Reports checked | بلاغات تحققت منها | |
| 97 | StillThere | Still there | ما زال موجودًا | |
| 98 | StopNavigation | Stop navigation | إيقاف الملاحة | |
| 99 | StopSharing | Stop sharing | إيقاف المشاركة | |
| 100 | TagFastest | Fastest | الأسرع | |
| 101 | TagNoTolls | No tolls | بلا رسوم | |
| 102 | TagShortest | Shortest | الأقصر | |
| 103 | Thanks | Thanks! | شكرًا! | |
| 104 | TipAddStop | Tip: press and hold the map to add a stop | تلميح: اضغط مطولًا على الخريطة لإضافة توقف | |
| 105 | Today | Today | اليوم | |
| 106 | TollPriceUnknown | Toll: price unknown | رسوم: السعر غير معروف | |
| 107 | TollRoad | Toll road | طريق برسوم | |
| 108 | Tomorrow | Tomorrow | غدًا | |
| 109 | TooLate | Too late to arrive by then: choose a later time. | فات الأوان للوصول في هذا الوقت: اختر وقتًا لاحقًا. | |
| 110 | TopLevel | Top level reached | وصلت إلى أعلى مستوى | |
| 111 | UnmuteVoice | Unmute voice | إلغاء كتم الصوت | |
| 112 | UnpavedAllowed | unpaved roads allowed | مع السماح بالطرق غير المعبدة | |
| 113 | WaitingForLocation | Waiting for your location. Enable GPS and try again. | بانتظار موقعك. فعّل GPS وحاول مجددًا. | |
| 114 | WarnOverLimitBy | Warn when over the limit by | التنبيه عند تجاوز الحد بمقدار | |
| 115 | WhatsWrongWithReport | What's wrong with this report? | ما المشكلة في هذا البلاغ؟ | |
| 116 | WhereTo | Where to? | إلى أين؟ | |
| 117 | Work | Work | العمل | |
| 118 | YourHomeAddress | Your home address | عنوان منزلك | |
| 119 | YourReport | Your report. Other drivers can confirm it. | بلاغك. يمكن للسائقين الآخرين تأكيده. | |
| 120 | YourWorkAddress | Your work address | عنوان عملك | |
| 121 | category FUEL | Fuel | وقود | |
| 122 | category PARKING | Parking | مواقف | |
| 123 | category FOOD | Food | طعام | |
| 124 | category EV_CHARGING | EV charging | شحن كهربائي | |
| 125 | category REST_AREA | R&R | استراحة | |
| 126 | category SPEED_CAMERA | Speed camera | كاميرا سرعة | |
| 127 | category SCHOOL_ZONE | School | مدرسة | |
| 128 | report POLICE | Police | شرطة | |
| 129 | report CRASH | Crash | حادث | |
| 130 | report HAZARD | Hazard | خطر | |
| 131 | report TRAFFIC_JAM | Traffic jam | ازدحام | |
| 132 | report ROAD_CLOSED | Road closed | طريق مغلق | |
| 133 | detail VISIBLE | Visible | ظاهرة | |
| 134 | detail HIDDEN | Hidden | مخفية | |
| 135 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 136 | detail MINOR | Minor | بسيط | |
| 137 | detail MAJOR | Major | كبير | |
| 138 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 139 | detail OBJECT_ON_ROAD | Object on road | جسم على الطريق | |
| 140 | detail POTHOLE | Pothole | حفرة | |
| 141 | detail STOPPED_VEHICLE | Stopped vehicle | مركبة متوقفة | |
| 142 | detail ANIMAL | Animal | حيوان | |
| 143 | detail FLOOD | Flood | فيضان | |
| 144 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 145 | detail MODERATE | Moderate | متوسط | |
| 146 | detail HEAVY | Heavy | شديد | |
| 147 | detail STANDSTILL | Standstill | متوقف تمامًا | |
| 148 | detail FULL | Fully closed | مغلق كليًا | |
| 149 | detail LANE_BLOCKED | Lane blocked | مسار مغلق | |
| 150 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 151 | vehicle CAR | Car | سيارة | |
| 152 | vehicle MOTORCYCLE | Motorcycle | دراجة نارية | |
| 153 | vehicle TAXI | Taxi | تاكسي | |
| 154 | flag FAKE | Fake report | بلاغ كاذب | |
| 155 | flag SPAM | Spam | رسائل مزعجة | |
| 156 | flag OFFENSIVE | Offensive | مسيء | |
| 157 | flag WRONG_LOCATION | Wrong location | موقع خاطئ | |
| 158 | map colours AUTO | Automatic (dark after sunset) | تلقائي (داكن بعد الغروب) | |
| 159 | map colours LIGHT | Always light | فاتح دائمًا | |
| 160 | map colours DARK | Always dark | داكن دائمًا | |
| 161 | limit ALWAYS | Always | دائمًا | |
| 162 | limit WHEN_SPEEDING | Only when speeding | عند تجاوز السرعة فقط | |
| 163 | limit NEVER | Never | أبدًا | |
| 164 | parked | Your car · parked 25 min ago | سيارتك · رُكنت منذ 25 د | |
| 165 | stop | Stop 1: KLCC | توقف 1: KLCC | |
| 166 | car parks | 3 car parks near your destination | 3 مواقف سيارات قرب وجهتك | |
| 167 | toll | Toll ~RM 6.80 | رسوم تقريبًا RM 6.80 | |
| 168 | toll partial | Toll RM 5.00 + ? | رسوم RM 5.00 أو أكثر | |
| 169 | summary | Motorcycle · avoiding tolls, highways | دراجة نارية · مع تجنب الرسوم والطرق السريعة | |
| 170 | reported | Police reported. Thanks! | تم الإبلاغ عن شرطة. شكرًا! | |
| 171 | nearby | Fuel nearby | وقود بالقرب منك | |
| 172 | none within | No fuel within 10 km | لا يوجد وقود ضمن 10 كم | |
| 173 | closure 3h | 3 hours | 3 ساعات | |
| 174 | notification | In 350 m: Navigating to KLCC | بعد 350 م: الملاحة إلى KLCC | |
