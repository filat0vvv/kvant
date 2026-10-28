"""Apply Kvant changes to the pinned, public exteraGram sources."""
import json
import os
import re
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(os.environ.get("SOURCE_ROOT", "source_code"))
JAVA = ROOT / "TMessagesProj/src/main/java"


def replace(text, old, new, count=1):
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"Source changed: expected {count} occurrences, got {actual}: {old[:90]!r}")
    return text.replace(old, new)


def remove_between(text, start, end):
    if text.count(start) != 1:
        raise RuntimeError(f"Source changed: missing/ambiguous block {start!r}")
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + text[b:]


def patch_boost():
    # The public upstream references Boost classes that it does not publish.
    # Remove those optional integrations; never emulate encryption with no-op methods.
    files = [
        "org/telegram/messenger/MessageObject.java",
        "org/telegram/messenger/ContactsController.java",
        "org/telegram/messenger/TranslateController.java",
        "org/telegram/messenger/Emoji.java",
        "org/telegram/ui/Cells/DialogCell.java",
        "org/telegram/ui/ChatActivity.java",
        "org/telegram/ui/Components/ChatActivityEnterView.java",
    ]
    pending = {}
    for name in files:
        text = (JAVA / name).read_text(encoding="utf-8")
        text = re.sub(r"^import com\.exteragram\.messenger\.boost\.[^;]+;\n", "", text, flags=re.M)
        if name.endswith("MessageObject.java"):
            text = replace(text, "    public BaseEncryptor currentEncryptor;\n", "")
        elif name.endswith("ContactsController.java"):
            text = replace(text, "(String) ZalgoFilter.filterMessage(firstName.trim())", "firstName.trim()")
            text = replace(text, "(String) ZalgoFilter.filterMessage(lastName.trim())", "lastName.trim()")
        elif name.endswith("TranslateController.java"):
            text = replace(text, " && !EncryptionHelper.isEncrypted(messageObject.messageOwner.message)", "")
        elif name.endswith("Emoji.java"):
            text = replace(text, "        cs = ZalgoFilter.filterMessage(cs);\n", "")
        elif name.endswith("ChatActivity.java"):
            text, n = re.subn(
                r"if \(BoostController\.isUserBooster\(\) && \(EncryptionHelper\.isEncrypted\(selectedObject\.messageOwner\.message\) \|\| selectedObject\.isDecrypted\)\) \{\n"
                r"\s*items\.add\(LocaleController\.getString\(R\.string\.Decrypt\)\);\n"
                r"\s*options\.add\(OPTION_DECRYPT\);\n"
                r"\s*icons\.add\(R\.drawable\.msg_permissions\);\n\s*\} else if",
                "if", text)
            if n != 2:
                raise RuntimeError("Expected exactly two Boost decrypt menu entries")
            text = remove_between(text, "            case OPTION_DECRYPT: {\n", "            case OPTION_HISTORY: {\n")
        elif name.endswith("ChatActivityEnterView.java"):
            text = remove_between(text, "            if (BoostController.isUserBooster()) {\n", "            if (scheduleButtonValue) {\n")
            text = replace(text,
                "        if (text != null && (editingMessageObject.originalText != null || editingMessageObject.isDecrypted)) {\n"
                "            text = EncryptionHelper.encryptMessage(text.toString(), dialog_id, editingMessageObject.currentEncryptor);\n"
                "        }\n", "")
            text = replace(text,
                "            if (EncryptionHelper.isEncrypted(editingMessageObject, null)) {\n"
                "                editingText = ChatUtils.getMessageText(EncryptionHelper.decryptMessage(editingMessageObject), null);\n"
                "            }\n", "")
        if re.search(r"\b(BaseEncryptor|BoostController|EncryptionHelper|ZalgoFilter|currentEncryptor)\b", text):
            raise RuntimeError(f"Unpatched Boost reference in {name}")
        pending[JAVA / name] = text
    for target, text in pending.items():
        target.write_text(text, encoding="utf-8")


def patch_app():
    app_name = os.environ.get("APP_NAME", "Kvant")
    app_id = os.environ.get("APP_ID", "com.kvant.messenger")
    channel = os.environ.get("CHANNEL", "kvant_official").lstrip("@")
    link = os.environ.get("LINK", "https://t.me/kvant_official")
    api_id = os.environ.get("TELEGRAM_API_ID", "")
    api_hash = os.environ.get("TELEGRAM_API_HASH", "")
    if not re.fullmatch(r"[1-9][0-9]*", api_id) or not re.fullmatch(r"[a-fA-F0-9]{32}", api_hash):
        raise RuntimeError("Set GitHub Actions secrets TELEGRAM_API_ID and TELEGRAM_API_HASH from my.telegram.org/apps")
    if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+", app_id):
        raise RuntimeError("APP_ID must be an Android package name")
    if not re.fullmatch(r"[A-Za-z0-9_]+", channel):
        raise RuntimeError("CHANNEL must contain only letters, digits and underscores")
    if not link.startswith("https://") or any(c in link for c in '\n\r"\\'):
        raise RuntimeError("LINK must be an HTTPS URL")

    patch_boost()
    template = Path(__file__).resolve().parents[1] / "templates/AutoProxyManager.java"
    (JAVA / "org/telegram/messenger/AutoProxyManager.java").write_text(template.read_text(encoding="utf-8"), encoding="utf-8")
    target = JAVA / "org/telegram/messenger/ApplicationLoader.java"
    text = target.read_text(encoding="utf-8")
    # Post to the UI queue after postInitApplication has finished initializing all accounts.
    text = replace(text, "        applicationInited = true;\n", "        applicationInited = true;\n        AndroidUtilities.runOnUIThread(AutoProxyManager::start, 1000);\n")
    target.write_text(text, encoding="utf-8")

    target = ROOT / "gradle.properties"
    text = target.read_text(encoding="utf-8")
    text = replace(text, "APP_PACKAGE=com.exteragram.messenger", "APP_PACKAGE=" + app_id)
    target.write_text(text, encoding="utf-8")
    # APP_HASH is a Java string literal, as required by buildConfigField.
    (ROOT / "API_KEYS").write_text(f"APP_ID={api_id}\nAPP_HASH={json.dumps(api_hash)}\nMAPS_V2_API=\n", encoding="utf-8")

    target = ROOT / "TMessagesProj/build.gradle"
    text = target.read_text(encoding="utf-8")
    for plugin in ("com.google.gms.google-services", "com.google.firebase.crashlytics"):
        text = replace(text, f"apply plugin: '{plugin}'", f"// Kvant: apply plugin: '{plugin}'")
    text = replace(text, 'storeFile file("config/extera.jks")', 'storeFile file("config/release.keystore")')
    for key, old, value in (
        ("storePassword", "SIGNING_KEY_STORE_PASSWORD", "kvant123"),
        ("keyAlias", "SIGNING_KEY_ALIAS", "release"),
        ("keyPassword", "SIGNING_KEY_PASSWORD", "kvant123"),
    ):
        text = replace(text, f'{key} localProperties.getProperty("{old}")', f'{key} "{value}"')
    target.write_text(text, encoding="utf-8")

    for target in (ROOT / "TMessagesProj/src/main/res").glob("values*/*.xml"):
        text = target.read_text(encoding="utf-8")
        text = re.sub(r">exteraGram(\s*Beta)?<", lambda _: ">" + escape(app_name) + "<", text, flags=re.I)
        target.write_text(text, encoding="utf-8")
    for target in (JAVA / "com/exteragram").rglob("*.java"):
        text = target.read_text(encoding="utf-8")
        text = text.replace('"exteragram"', json.dumps(channel)).replace('"exteraSquad"', json.dumps(channel))
        text = text.replace("https://exteragram.app", link)
        target.write_text(text, encoding="utf-8")
    print("Kvant patch applied: optional Boost integrations removed; Telegram secret chats remain upstream code.")


if __name__ == "__main__":
    patch_app()
