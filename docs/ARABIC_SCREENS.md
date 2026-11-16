# Arabic screens: review sheet

Every Arabic text on Jalanna screens next to the English (`ArabicStrings` in
`app/src/main/java/com/roadpulse/app/i18n/Strings.kt`). Voice prompts are in `ARABIC_PROMPTS.md`.
Write corrections in the last column. Debug-only screens (simulation, field-test flags, laptop server) stay in English.

Regenerate: `gradlew :app:testDebugUnitTest --tests *StringsTest*` writes `app/build/arabic_screens.tsv`.

| # | Key | English | العربية (current) | OK? / better wording |
|---|---|---|---|---|
| 1 | AddStop | Add stop | أضف توقفًا | |
| 2 | Allow | Allow | سماح | |
| 3 | AllowUnpaved | Allow unpaved roads | السماح بالطرق غير المعبدة | |
| 4 | ArriveBy | Arrive by | الوصول قبل | |
| 5 | Arrived | You have arrived | لقد وصلت | |
| 6 | AvoidFerries | Avoid ferries | تجنّب العبّارات | |
| 7 | AvoidFerriesShort | ferries | العبّارات | |
| 8 | AvoidHighways | Avoid highways | تجنّب الطرق السريعة | |
| 9 | AvoidHighwaysShort | highways | الطرق السريعة | |
| 10 | AvoidTolls | Avoid toll roads | تجنّب الطرق ذات الرسوم | |
| 11 | AvoidTollsShort | tolls | الرسوم | |
| 12 | Back | Back | رجوع | |
| 13 | Badges | Badges | الأوسمة | |
| 14 | Cancel | Cancel | إلغاء | |
| 15 | ChooseDestinationFirst | Choose a destination first, then add stops | اختر وجهة أولًا، ثم أضف التوقفات | |
| 16 | Clear | Clear | مسح | |
| 17 | ClearSavedPlaces | Clear saved places | مسح الأماكن المحفوظة | |
| 18 | Close | Close | إغلاق | |
| 19 | CouldNotLoadPlaces | Could not load places. Check your connection. | تعذّر تحميل الأماكن. تحقق من الاتصال. | |
| 20 | CouldNotSendReport | Could not send report. Check your connection and try again. | تعذّر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا. | |
| 21 | DataDeleted | Your data was deleted | تم حذف بياناتك | |
| 22 | Delete | Delete | حذف | |
| 23 | DeleteData | Delete my data | حذف بياناتي | |
| 24 | DeleteDataConfirm | This deletes your reports, votes, flags, nickname and share links from the server, and your saved places, planned drives and parking spot from this phone. It cannot be undone. | سيحذف هذا بلاغاتك وتصويتاتك وإبلاغاتك واسمك المستعار وروابط المشاركة من الخادم، وأماكنك المحفوظة ورحلاتك المخطط لها ومكان ركن سيارتك من هذا الهاتف. لا يمكن التراجع عن ذلك. | |
| 25 | DeleteFailed | Could not delete your data on the server. Check the connection and try again. | تعذّر حذف بياناتك من الخادم. تحقق من الاتصال وحاول مجددًا. | |
| 26 | Destination | Destination | الوجهة | |
| 27 | Done | Done | تم | |
| 28 | DriveHere | Drive here | انطلق إلى هنا | |
| 29 | DroppedPin | Dropped pin | دبوس على الخريطة | |
| 30 | ExportData | Export my data | تصدير بياناتي | |
| 31 | ExportFailed | Could not get your data from the server. | تعذّر الحصول على بياناتك من الخادم. | |
| 32 | FlatMap | Flat map while driving (2D) | خريطة مسطحة أثناء القيادة (ثنائية الأبعاد) | |
| 33 | ForgetParking | Forget parking spot | انسَ مكان الركن | |
| 34 | Free | free | مجاني | |
| 35 | GoNow | Go now | انطلق الآن | |
| 36 | HelpTraffic | Help with live traffic | ساعد في معرفة حركة المرور | |
| 37 | HelpTrafficText | When on, Jalanna sends your speed and position in short anonymous pieces while you navigate: no account or device ID, nothing from the first and last 500 m of a trip, and the server keeps only average road speeds. Turn off at any time. | عند التفعيل، يرسل Jalanna سرعتك وموقعك على شكل أجزاء قصيرة مجهولة الهوية أثناء الملاحة: بلا حساب أو معرّف جهاز، ولا شيء من أول وآخر 500 م من الرحلة، ويحتفظ الخادم بمتوسط سرعات الطرق فقط. يمكنك إيقافه في أي وقت. | |
| 38 | Hide | Hide | إخفاء | |
| 39 | Home | Home | المنزل | |
| 40 | HowLong | How long? | كم المدة؟ | |
| 41 | HowPointsWork | 10 points per report, 5 when another driver confirms it, 2 for checking someone's report. A report removed by moderators costs 20. | 10 نقاط لكل بلاغ، و5 عندما يؤكده سائق آخر، و2 عند التحقق من بلاغ غيرك. البلاغ الذي يحذفه المشرفون يخصم 20. | |
| 42 | KeepRoute | No thanks | لا، شكرًا | |
| 43 | Kmh | km/h | كم/س | |
| 44 | Language | Language | اللغة | |
| 45 | LeaveLater | Leave later | انطلق لاحقًا | |
| 46 | LeaveRemindersChannel | Time to leave reminders | تذكيرات وقت الانطلاق | |
| 47 | LocationUnavailable | Location unavailable | الموقع غير متاح | |
| 48 | LocationUnavailableReport | Location unavailable, cannot place report | الموقع غير متاح، لا يمكن إضافة البلاغ | |
| 49 | MapColours | Map colours | ألوان الخريطة | |
| 50 | MapView | Map view | عرض الخريطة | |
| 51 | MusicControls | Music controls while driving | التحكم بالموسيقى أثناء القيادة | |
| 52 | MusicControlsHelp | Play, pause and skip in Spotify, YouTube Music and other apps from the driving screen. | شغّل وأوقف وتنقّل بين المقاطع في Spotify وYouTube Music وغيرها من شاشة القيادة. | |
| 53 | MusicNeedsAccess | Android asks for notification access for this. Jalanna uses it only to see which music is playing, not to read your notifications. | سيطلب أندرويد إذن الوصول إلى الإشعارات. يستخدمه Jalanna فقط لمعرفة الموسيقى المشغّلة، ولا يقرأ إشعاراتك. | |
| 54 | MuteVoice | Mute voice | كتم الصوت | |
| 55 | MuteVoiceSetting | Mute voice | كتم الصوت | |
| 56 | Navigation | Navigation | الملاحة | |
| 57 | NextTrack | Next track | المقطع التالي | |
| 58 | Nickname | Nickname | الاسم المستعار | |
| 59 | NicknameHint | Optional. Shown on your profile; no real name needed. | اختياري. يظهر في ملفك الشخصي، ولا حاجة لاسمك الحقيقي. | |
| 60 | NorthUp | North always up | الشمال للأعلى دائمًا | |
| 61 | NotNow | Not now | ليس الآن | |
| 62 | NotThere | Not there | غير موجود | |
| 63 | NotificationChannel | Shown while Jalanna is guiding a drive | يظهر أثناء إرشاد Jalanna لرحلتك | |
| 64 | NotificationStop | Stop | إيقاف | |
| 65 | Overview | Overview | عرض المسار | |
| 66 | Paid | paid | مدفوع | |
| 67 | ParkedHere | Parked here | ركنت هنا | |
| 68 | ParkingSpotSaved | Parking spot saved | تم حفظ مكان الركن | |
| 69 | Pause | Pause | إيقاف مؤقت | |
| 70 | PlaceNameHint | Name, e.g. Gym | الاسم، مثلًا النادي | |
| 71 | PlanDrive | Plan this drive | خطط لهذه الرحلة | |
| 72 | PlannedDrives | Planned drives | رحلات مخطط لها | |
| 73 | Play | Play | تشغيل | |
| 74 | PreviousTrack | Previous track | المقطع السابق | |
| 75 | Privacy | Privacy | الخصوصية | |
| 76 | PrivacyPolicy | Privacy policy | سياسة الخصوصية | |
| 77 | PrivacyText | Home, Work and recent places are stored only on this phone. | يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط. | |
| 78 | Profile | Profile | الملف الشخصي | |
| 79 | ProfileNeedsServer | Your profile needs the Jalanna server. Turn on "Use my laptop's server" in Settings. | يحتاج ملفك الشخصي إلى خادم Jalanna. فعّل "Use my laptop's server" في الإعدادات. | |
| 80 | RemoveStop | Remove stop | إزالة التوقف | |
| 81 | Report | Report | بلّغ | |
| 82 | ReportAbuse | Report abuse | الإبلاغ عن إساءة | |
| 83 | Route | Route | المسار | |
| 84 | Save | Save | حفظ | |
| 85 | SavePlace | Save place | حفظ المكان | |
| 86 | SavedPlacesCleared | Saved places cleared | تم مسح الأماكن المحفوظة | |
| 87 | SearchByVoice | Search by voice | البحث بالصوت | |
| 88 | Send | Send | إرسال | |
| 89 | SetAddress | Set address | عيّن العنوان | |
| 90 | SetReminder | Remind me | ذكّرني | |
| 91 | Settings | Settings | الإعدادات | |
| 92 | SettingsButton | Voice guidance settings | الإعدادات | |
| 93 | ShareDrive | Share drive | شارك الرحلة | |
| 94 | ShareFailed | Could not share the drive. Check the connection to the server. | تعذّرت مشاركة الرحلة. تحقق من الاتصال بالخادم. | |
| 95 | SharingLive | Sharing live | المشاركة مباشرة | |
| 96 | Show | Show | عرض | |
| 97 | ShowCompass | Show compass | إظهار البوصلة | |
| 98 | ShowMyLocation | Show my location | أظهر موقعي | |
| 99 | ShowSpeedLimit | Show speed limit | إظهار حد السرعة | |
| 100 | ShowSpeedometer | Show speedometer | إظهار عدّاد السرعة | |
| 101 | SimulateDrive | Simulate a drive | محاكاة رحلة | |
| 102 | Skip | Skip | تخطٍّ | |
| 103 | SoundWhenSpeeding | Sound when speeding | صوت تنبيه عند تجاوز السرعة | |
| 104 | Speed | Speed | السرعة | |
| 105 | StatConfirmed | Confirmed by others | أكّدها آخرون | |
| 106 | StatReports | Reports | البلاغات | |
| 107 | StatVotes | Reports checked | بلاغات تحققت منها | |
| 108 | StillThere | Still there | ما زال موجودًا | |
| 109 | StopNavigation | Stop navigation | إيقاف الملاحة | |
| 110 | StopSharing | Stop sharing | إيقاف المشاركة | |
| 111 | TagFastest | Fastest | الأسرع | |
| 112 | TagNoTolls | No tolls | بلا رسوم | |
| 113 | TagShortest | Shortest | الأقصر | |
| 114 | TakeRoute | Take it | اسلكه | |
| 115 | Thanks | Thanks! | شكرًا! | |
| 116 | TipAddStop | Tip: press and hold the map to add a stop | تلميح: اضغط مطولًا على الخريطة لإضافة توقف | |
| 117 | Today | Today | اليوم | |
| 118 | TollPriceUnknown | Toll: price unknown | رسوم: السعر غير معروف | |
| 119 | TollRoad | Toll road | طريق برسوم | |
| 120 | Tomorrow | Tomorrow | غدًا | |
| 121 | TooLate | Too late to arrive by then: choose a later time. | فات الأوان للوصول في هذا الوقت: اختر وقتًا لاحقًا. | |
| 122 | TopLevel | Top level reached | وصلت إلى أعلى مستوى | |
| 123 | TrafficConsentTitle | Help with live traffic? | هل تساعد في معرفة حركة المرور؟ | |
| 124 | UnmuteVoice | Unmute voice | إلغاء كتم الصوت | |
| 125 | UnpavedAllowed | unpaved roads allowed | مع السماح بالطرق غير المعبدة | |
| 126 | WaitingForLocation | Waiting for your location. Enable GPS and try again. | بانتظار موقعك. فعّل GPS وحاول مجددًا. | |
| 127 | WarnOverLimitBy | Warn when over the limit by | التنبيه عند تجاوز الحد بمقدار | |
| 128 | WhatsWrongWithReport | What's wrong with this report? | ما المشكلة في هذا البلاغ؟ | |
| 129 | WhereTo | Where to? | إلى أين؟ | |
| 130 | Work | Work | العمل | |
| 131 | YourHomeAddress | Your home address | عنوان منزلك | |
| 132 | YourReport | Your report. Other drivers can confirm it. | بلاغك. يمكن للسائقين الآخرين تأكيده. | |
| 133 | YourWorkAddress | Your work address | عنوان عملك | |
| 134 | category FUEL | Fuel | وقود | |
| 135 | category PARKING | Parking | مواقف | |
| 136 | category FOOD | Food | طعام | |
| 137 | category EV_CHARGING | EV charging | شحن كهربائي | |
| 138 | category REST_AREA | R&R | استراحة | |
| 139 | category SPEED_CAMERA | Speed camera | كاميرا سرعة | |
| 140 | category SCHOOL_ZONE | School | مدرسة | |
| 141 | report POLICE | Police | شرطة | |
| 142 | report CRASH | Crash | حادث | |
| 143 | report HAZARD | Hazard | خطر | |
| 144 | report TRAFFIC_JAM | Traffic jam | ازدحام | |
| 145 | report ROAD_CLOSED | Road closed | طريق مغلق | |
| 146 | detail VISIBLE | Visible | ظاهرة | |
| 147 | detail HIDDEN | Hidden | مخفية | |
| 148 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 149 | detail MINOR | Minor | بسيط | |
| 150 | detail MAJOR | Major | كبير | |
| 151 | detail OTHER_SIDE | Other side | في الاتجاه المعاكس | |
| 152 | detail OBJECT_ON_ROAD | Object on road | جسم على الطريق | |
| 153 | detail POTHOLE | Pothole | حفرة | |
| 154 | detail STOPPED_VEHICLE | Stopped vehicle | مركبة متوقفة | |
| 155 | detail ANIMAL | Animal | حيوان | |
| 156 | detail FLOOD | Flood | فيضان | |
| 157 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 158 | detail MODERATE | Moderate | متوسط | |
| 159 | detail HEAVY | Heavy | شديد | |
| 160 | detail STANDSTILL | Standstill | متوقف تمامًا | |
| 161 | detail FULL | Fully closed | مغلق كليًا | |
| 162 | detail LANE_BLOCKED | Lane blocked | مسار مغلق | |
| 163 | detail CONSTRUCTION | Construction | أعمال إنشاء | |
| 164 | vehicle CAR | Car | سيارة | |
| 165 | vehicle MOTORCYCLE | Motorcycle | دراجة نارية | |
| 166 | vehicle TAXI | Taxi | تاكسي | |
| 167 | flag FAKE | Fake report | بلاغ كاذب | |
| 168 | flag SPAM | Spam | رسائل مزعجة | |
| 169 | flag OFFENSIVE | Offensive | مسيء | |
| 170 | flag WRONG_LOCATION | Wrong location | موقع خاطئ | |
| 171 | map colours AUTO | Automatic (dark after sunset) | تلقائي (داكن بعد الغروب) | |
| 172 | map colours LIGHT | Always light | فاتح دائمًا | |
| 173 | map colours DARK | Always dark | داكن دائمًا | |
| 174 | limit ALWAYS | Always | دائمًا | |
| 175 | limit WHEN_SPEEDING | Only when speeding | عند تجاوز السرعة فقط | |
| 176 | limit NEVER | Never | أبدًا | |
| 177 | parked | Your car · parked 25 min ago | سيارتك · رُكنت منذ 25 د | |
| 178 | stop | Stop 1: KLCC | توقف 1: KLCC | |
| 179 | car parks | 3 car parks near your destination | 3 مواقف سيارات قرب وجهتك | |
| 180 | toll | Toll ~RM 6.80 | رسوم تقريبًا RM 6.80 | |
| 181 | toll partial | Toll RM 5.00 + ? | رسوم RM 5.00 أو أكثر | |
| 182 | summary | Motorcycle · avoiding tolls, highways | دراجة نارية · مع تجنب الرسوم والطرق السريعة | |
| 183 | reported | Police reported. Thanks! | تم الإبلاغ عن شرطة. شكرًا! | |
| 184 | nearby | Fuel nearby | وقود بالقرب منك | |
| 185 | none within | No fuel within 10 km | لا يوجد وقود ضمن 10 كم | |
| 186 | closure 3h | 3 hours | 3 ساعات | |
| 187 | notification | In 350 m: Navigating to KLCC | بعد 350 م: الملاحة إلى KLCC | |
