# Ruflo Companion for Android

`android/` هو تطبيق Android أصلي مرافق لـ Ruflo، مكتوب بـ Kotlin وJetpack Compose. يقدّم شاشة مراقبة وتحكم للوكلاء عبر **بوابة Ruflo متوافقة**. لا يستبدل التطبيق واجهة Ruflo أو خادمها، بل يتصل فقط بالطبقة التي تستضيفها أنت.

## المتطلبات والبناء

يتطلب البناء Android Studio Ladybug أو أحدث، وJDK 17، وAndroid SDK Platform 35. يحتوي هذا المجلد على Gradle Wrapper، ولذلك يمكن تنفيذ الأوامر التالية من داخله:

```bash
./gradlew test
./gradlew assembleDebug
```

ينتج ملف APK التجريبي في `app/build/outputs/apk/debug/`.

لبناء إصدار موقّع، احفظ بيانات المخزن في ملف خصائص خاص خارج Git ثم مرّر مساره كمتغير بيئة:

```bash
RUFLO_SIGNING_PROPERTIES=/path/to/signing.properties ./gradlew assembleRelease
```

يجب أن يحوي الملف `storeFile` و`storePassword` و`keyAlias` و`keyPassword`. لا تضف هذا الملف أو مخزن المفاتيح إلى المستودع.

## إعداد المصادقة والتفويض

لا يقبل التطبيق كلمات مرور أو رموز وصول ملصقة يدويًا. بدلاً من ذلك، يستخدم **OpenID Connect / OAuth 2.0 Authorization Code مع PKCE** في متصفح نظام Android. لهذا الغرض يجب أن تسجّل عميلًا عامًا في موفر الهوية الموثوق لبوابتك، بالمعرّف التالي:

| إعداد العميل | القيمة المطلوبة |
|---|---|
| نوع العميل | Public native/mobile client |
| Redirect URI | `io.ruv.ruflo.android:/oauth2redirect` |
| Grant type | Authorization Code مع PKCE (S256) |
| النطاقات المطلوبة | `openid profile agents.read agents.control` |
| عنوان جهة الإصدار | عنوان HTTPS نفسه الذي يُدخل في التطبيق، مثل `https://ruflo.example.com` |

> يجب أن يكون عنوان البوابة جهة إصدار OIDC قادرة على نشر اكتشاف OpenID Connect في `/.well-known/openid-configuration`. يستخدم التطبيق متصفح النظام للمصادقة؛ لا يرى التطبيق كلمة المرور ولا يخزنها.

يخزن التطبيق **رمز الوصول قصير العمر فقط**، مشفّرًا بواسطة `EncryptedSharedPreferences` ومفتاح Android Keystore. لا يطلب `offline_access` ولا يحتفظ برمز تحديث طويل العمر. عند انتهاء الجلسة يجب المصادقة من جديد.

## عقد واجهة البوابة

يتصل التطبيق بالنقاط التالية. جميعها تتطلب `Authorization: Bearer <access-token>`، وHTTPS، وتحققًا في الخادم من التوقيع و`aud` و`iss` ومدة الرمز والنطاقات.

| الطلب | النطاق الإلزامي | الاستجابة المقبولة | الغرض |
|---|---|---|---|
| `GET /api/v1/agents` | `agents.read` | مصفوفة وكلاء، أو كائن JSON يحتوي `agents` أو `data` كمصفوفة | عرض حالة الوكلاء |
| `POST /api/v1/agents/{agentId}/stop` | `agents.control` | `204 No Content` أو JSON | إرسال طلب إيقاف الوكيل |
| `POST /api/v1/agents/{agentId}/restart` | `agents.control` | `204 No Content` أو JSON | إرسال طلب إعادة تشغيل الوكيل |

يقبل كل وكيل الحقول `id` و`name` و`role` و`status` و`currentTask`. تدعم القراءة أيضًا الاسمين البديلين `agent_id` و`type` و`task` لتسهيل التكامل مع بوابات قائمة.

مثال استجابة `GET /api/v1/agents`:

```json
{
  "agents": [
    {
      "id": "reviewer-01",
      "name": "مراجع الأمن",
      "role": "security-reviewer",
      "status": "running",
      "currentTask": "فحص التغييرات الأخيرة"
    }
  ]
}
```

## ضوابط التحكم

أزرار **إيقاف** و**إعادة تشغيل** لا تظهر فعّالة إلا بعد نجاح المصادقة ومنح نطاق `agents.control`. وقبل كل أمر يعرض التطبيق مربع تأكيد يذكر الوكيل والإجراء. بعد التنفيذ يعيد التطبيق تحميل قائمة الوكلاء.

> **مسؤولية البوابة:** لا تُعامل تطبيق الهاتف كحد أمان. يجب أن تفرض البوابة النطاق `agents.control` من جهة الخادم، وتتحقق من هوية المتصل، وتسجل هوية المستخدم والوكيل والإجراء والنتيجة والوقت في سجل تدقيق غير قابل للعبث. يجب أن تعيد `401` للجلسة غير الصالحة و`403` للجلسة التي تفتقر إلى النطاق المطلوب.

التطبيق لا يقبل `http://` ولا عناوين فيها بيانات اعتماد مضمنة، كما أن إعداد Android يمنع الاتصالات النصية غير المشفّرة.
