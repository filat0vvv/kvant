"""Remove unavailable optional Boost integrations from the public base."""
import re
from pathlib import Path
JAVA = None

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



def apply(java):
    global JAVA
    JAVA = java
    patch_boost()

