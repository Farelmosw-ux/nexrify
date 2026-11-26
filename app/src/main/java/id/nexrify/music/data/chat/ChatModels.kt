package id.nexrify.music.data.chat

enum class SendStatus { SENDING, SENT, FAILED }

data class ChatReply(val seq: Long, val username: String, val text: String, val deleted: Boolean = false)

data class ChatMessage(
    val seq: Long,
    val uid: String,
    val username: String,
    val display: String,
    val badges: List<String>,
    val av: String,
    val text: String,
    val ts: Long,
    val edited: Long,
    val deleted: Boolean,
    val announce: Boolean,
    val mentions: List<String>,
    val reply: ChatReply?,
    /** emoji → daftar uid yang bereaksi */
    val reactions: Map<String, List<String>>,
    val status: SendStatus = SendStatus.SENT,
    /** Hanya untuk pesan yang belum terkirim (seq negatif). */
    val localId: String? = null,
    val pinned: Boolean = false,
    val poll: ChatPoll? = null,
    /** Pesan suara (hanya Dev, Admin, Staf yang bisa mengirim). */
    val voice: ChatVoice? = null,
    val image: DmImage? = null,
    val call: DmCall? = null,
    val onceText: Boolean = false,
    val onceViewed: Boolean = false
)

/** Catatan panggilan suara di percakapan. [status]: ended, missed, atau declined; [dur] dalam detik. */
data class DmCall(val status: String, val dur: Long)

/** Foto di chat pribadi. [w] dan [h] dipakai untuk rasio gelembung sebelum gambar selesai diunduh. */
data class DmImage(val id: String, val w: Int, val h: Int, val once: Boolean = false, val viewed: Boolean = false)

/** Kartu pratinjau tautan (YouTube, Spotify, TikTok, SoundCloud). */
data class LinkPreview(val site: String, val title: String, val author: String, val thumb: String, val url: String)

/** Status mood dan lagu yang sedang didengar seorang teman. Kosong = tidak ada atau tidak terlihat. */
data class PeerExtra(val moodEmoji: String, val moodText: String, val npTitle: String, val npArtist: String)

/** wave = deretan digit 0-9, tinggi tiap batang gelombang. */
data class ChatVoice(val id: String, val durMs: Long, val wave: String)

data class ChatPollOption(val text: String, val votes: Int)

data class ChatPoll(val question: String, val options: List<ChatPollOption>, val total: Int, val closesAt: Long, val closed: Boolean)

data class ChatTopTrack(val title: String, val artist: String)

data class ChatProfile(
    val uid: String,
    val username: String,
    val display: String,
    val av: String,
    val joined: Long,
    val bio: String,
    val status: String,
    val messages: Int,
    val reactionsReceived: Int,
    val liked: Int,
    val playlists: Int,
    val top: List<ChatTopTrack>,
    val badges: List<String>,
    val blockedByMe: Boolean = false,
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val presenceHidden: Boolean = false,
    val profileHidden: Boolean = false,
    val musicHidden: Boolean = false,
    val chatBlocked: Boolean = false,
    val donateRank: Int = 0,
    val titleOn: Boolean = true,
    /** Title yang dipasang pengguna (maks 3), urut tampil. */
    val titles: List<ProfileTitle> = emptyList(),
    /** Semua title yang dimiliki (hanya terisi untuk profil sendiri). */
    val titlesOwned: List<ProfileTitle> = emptyList(),
    val titlesMax: Int = 3,
    /** Kemajuan misi tiap title, kunci = kode title (hanya profil sendiri). */
    val titlesInfo: Map<String, TitleProgress> = emptyMap(),
    /** Status pengajuan Konten Kreator (hanya profil sendiri). */
    val creatorApp: CreatorApp? = null,
    val followers: Int = 0,
    val following: Int = 0,
    /** Teman = saling ikuti. */
    val friends: Int = 0,
    val iFollow: Boolean = false,
    val followsMe: Boolean = false,
    val friend: Boolean = false,
    val followHidden: Boolean = false,
    val hasCover: Boolean = false,
    val coverV: String = ""
)

/** Satu baris di daftar pengikut / mengikuti / teman. */
data class FollowUser(
    val uid: String,
    val username: String,
    val display: String,
    val av: String,
    val iFollow: Boolean,
    val followsMe: Boolean,
    val friend: Boolean
)

data class FollowPage(val items: List<FollowUser>, val next: Int, val total: Int, val hidden: Boolean)

/**
 * Kemajuan misi satu title (profil sendiri).
 * Title balapan (marathon: milidetik, streak: hari): [mine] punyamu, [need] angka minimal untuk merebut, [holderValue] milik pemegang sekarang.
 * Title pencapaian (mubar, hours, circle): [cur] dari [goal].
 */
data class TitleProgress(
    val mine: Long = 0L, val need: Long = 0L, val min: Long = 0L, val resetAt: Long = 0L,
    val hasHolder: Boolean = false, val holderName: String = "", val holderValue: Long = 0L, val holderMe: Boolean = false,
    val cur: Long = 0L, val goal: Long = 0L
)

/** Title pengguna. code: donor, marathon, streak, creator, mubar, hours, circle. platform: tiktok / youtube / both (kreator). */
data class ProfileTitle(val code: String, val platform: String = "", val rank: Int = 0, val n: Int = 0)

/** Status pengajuan Konten Kreator: pending, approved, rejected, revoked. */
data class CreatorApp(val status: String, val platform: String, val createdAt: Long, val reviewedAt: Long, val reason: String)

/** Isi formulir pengajuan Konten Kreator. */
data class CreatorForm(
    val links: List<String>,
    val channelName: String,
    val whatsapp: String,
    val followers: Long,
    val content: String,
    val note: String,
    val agree: Boolean
)

data class ChatUserHit(val username: String, val uid: String, val display: String)

/** Kejadian yang relevan untuk pengguna saat Global Chat sedang dibuka. */
data class ChatEvent(val kind: String, val message: ChatMessage)

/** Notifikasi dari server untuk notifikasi HP: k = m (mention), r (balasan), x (reaksi), a (pengumuman). */
data class ChatNotif(val kind: String, val seq: Long, val from: String, val text: String, val ts: Long)

data class ChatSeen(val uid: String, val seq: Long)

data class ChatFeed(val ev: Long, val reset: Boolean, val typing: List<String>, val messages: List<ChatMessage>, val seen: List<ChatSeen> = emptyList())

/** All server-approved chat emojis. Keep synchronized with Nexrify-Web-API/api/_chat.js. */
val CHAT_EMOJIS = listOf(
    "👍", "👎", "😂", "😮", "😭", "🙏", "😁", "😡",
    "❌", "✅", "❤️", "🗿", "🔥", "🥰", "👏", "🤔",
    "🤯", "😱", "😢", "🎉", "🤩", "🤮", "💩", "👌",
    "🤡", "😍", "💯", "🤣", "⚡", "🏆", "💔", "😐",
    "🤨", "🍓", "😈", "🥱", "💀", "👀", "🫡", "🥹",
    "😎", "🙌", "💪", "🤝", "😀", "😃", "😄", "😆",
    "😅", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉",
    "😌", "😘", "😗", "😙", "😚", "😋", "😛", "😝",
    "😜", "🤪", "🧐", "🤓", "🥸", "🥳", "🙂‍↕️", "🙂‍↔️",
    "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️",
    "😣", "😖", "😫", "😩", "🥺", "😤", "😠", "🤬",
    "😳", "🥵", "🥶", "😶‍🌫️", "😨", "😰", "😥", "😓",
    "🫣", "🤗", "🫢", "🤭", "🤫", "🤥", "😶", "😑",
    "😬", "🫠", "🙄", "😯", "😦", "😧", "😲", "😴",
    "🤤", "😪", "😵", "😵‍💫", "🫥", "🤐", "🥴", "🤢",
    "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "👿", "👹",
    "👺", "☠️", "👻", "👽", "👾", "🤖", "🎃", "🩷",
    "🧡", "💛", "💚", "💙", "🩵", "💜", "🤎", "🖤",
    "🩶", "🤍", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓",
    "💗", "💖", "💘", "💝", "💟", "♥️", "🫶", "💌",
    "💋", "💐", "💍", "💎", "👋", "🤚", "🖐️", "✋",
    "🖖", "🫱", "🫲", "🫳", "🫴", "🤌", "🤏", "✌️",
    "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆",
    "👇", "☝️", "🫵", "✊", "👊", "🤛", "🤜", "👐",
    "🤲", "✍️", "💅", "🤳", "🦾", "🦿", "🦵", "🦶",
    "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴",
    "👁️", "👅", "👄", "🫦", "⌚", "📱", "📲", "💻",
    "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💽", "💾", "💿",
    "📀", "📼", "📷", "📸", "📹", "🎥", "📽️", "🎞️",
    "📞", "☎️", "📟", "📠", "🔋", "🪫", "🔌", "💡",
    "🔦", "🕯️", "🪔", "🧯", "🛢️", "🛍️", "💸", "💵",
    "💴", "💶", "💷", "💰", "🪙", "💳", "🧾", "⚖️",
    "🪜", "🧰", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪓",
    "🪚", "🔩", "⚙️", "🪛", "🗜️", "⚗️", "🧪", "🧫",
    "🧬", "🔬", "🔭", "📡", "💉", "🩸", "💊", "🩹",
    "🩺", "🚪", "🛏️", "🛋️", "🪑", "🚽", "🚿", "🛁",
    "🪞", "🪟", "🧴", "🧷", "🧹", "🧺", "🧻", "🪣",
    "🧼", "🫧", "🪥", "🧽", "🛒", "🚬", "⚰️", "🪦",
    "⚱️", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯",
    "🕎", "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊",
    "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒",
    "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳",
    "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮",
    "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️",
    "🅱️", "🆎", "🆑", "🅾️", "🆘", "⭕", "🛑", "⛔",
    "📛", "🚫", "💢", "♨️", "🚷", "🚯", "🚳", "🚱",
    "🔞", "📵", "🚭", "❗", "❕", "❓", "❔", "‼️",
    "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️",
    "🔰", "♻️", "🈯", "💹", "❇️", "✳️", "❎", "🌐",
    "💠", "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿", "🅿️",
    "🛗", "🈳", "🈂️", "🛂", "🛃", "🛄", "🛅", "🔣",
    "🔤", "🔡", "🔠", "🔢", "#️⃣", "*️⃣", "0️⃣", "1️⃣",
    "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣",
    "🔟", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫",
    "⚪", "🟤", "🔶", "🔷", "🔸", "🔹", "🔺", "🔻",
    "🔘", "🔳", "🔲", "▪️", "▫️", "◾", "◽", "◼️",
    "◻️", "⬛", "⬜", "🟥", "🟧", "🟨", "🟩", "🟦",
    "🟪", "🟫", "⬆️", "⬇️", "⬅️", "➡️", "↗️", "↘️",
    "↙️", "↖️", "↕️", "↔️", "↪️", "↩️", "⤴️", "⤵️",
    "🔃", "🔄", "🔙", "🔚", "🔛", "🔜", "🔝", "🏁",
    "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇮🇩",
    "🇺🇸", "🇬🇧", "🇯🇵", "🇰🇷", "🇨🇳", "🇸🇬", "🇲🇾", "🇹🇭",
    "🇵🇭", "🇻🇳", "🇧🇳", "🇦🇺", "🇨🇦", "🇫🇷", "🇩🇪", "🇮🇹",
    "🇪🇸", "🇧🇷", "🇷🇺", "🇮🇳", "🇵🇰", "🇸🇦", "🇹🇷", "🇳🇱",
    "🇧🇪", "🇨🇭", "🇸🇪", "🇳🇴", "🇫🇮", "🇳🇿", "🇦🇷", "🇲🇽",
    "🇪🇬", "🇿🇦", "🇺🇦", "🇵🇸", "🇮🇱", "🇵🇱", "🇵🇹", "🇬🇷",
    "🇦🇪", "🇰🇵", "🇭🇰", "🇹🇼", "🇲🇴", "🇲🇲", "🇰🇭", "🇱🇦",
    "🇧🇩", "🇱🇰", "🇳🇵", "🇲🇳", "🇮🇷", "🇮🇶", "🇶🇦", "🇰🇼",
    "🇴🇲", "🇾🇪", "🇯🇴", "🇱🇧", "🇲🇦", "🇩🇿", "🇹🇳", "🇰🇪",
    "🇳🇬", "🇬🇭", "🇪🇹", "🇨🇱", "🇨🇴", "🇵🇪", "🇨🇺", "🇯🇲",
    "🇨🇷", "🇵🇦", "🇺🇾", "🇵🇾", "🇪🇨", "🇮🇸", "🇮🇪", "🇩🇰",
    "🇨🇿", "🇦🇹", "🇭🇺", "🇷🇴", "🇧🇬", "🇭🇷", "🇷🇸", "🇸🇰",
    "🇸🇮", "🇱🇹", "🇱🇻", "🇪🇪", "🇲🇻", "🇫🇯", "🫂", "🫤",
    "🫨", "🪿", "🪼", "🪻", "🫚", "🫛", "🪭", "🪮",
    "🪇", "🪈", "🪯", "🛜", "🫎", "🫏", "🪽", "🪾",
    "🫆", "🫩", "🪉", "🪏",
)
/** Reaksi cepat yang tampil di bar atas menu pesan. Sisanya muncul saat panah ditekan. */
val CHAT_EMOJIS_QUICK = listOf("👍", "❤️", "😂", "😮", "😭", "🙏", "🔥")
const val CHAT_MAX_LENGTH = 500

/* ---------------- pesan langsung (DM) ---------------- */

data class DmReply(val seq: Long, val uid: String, val text: String)

data class DmMessage(
    val seq: Long,
    val uid: String,
    val text: String,
    val ts: Long,
    val edited: Long,
    val deleted: Boolean,
    val ev: Long,
    val reply: DmReply?,
    val status: SendStatus = SendStatus.SENT,
    val localId: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val song: DmSong? = null,
    val forwarded: Boolean = false,
    val expires: Long = 0,
    val voice: ChatVoice? = null
)

/** Kartu lagu yang dikirim lewat chat pribadi. */
data class DmSong(val id: String, val title: String, val artist: String, val thumb: String, val url: String, val dur: Long)

data class DmPeer(val uid: String, val username: String, val display: String, val av: String)

data class DmConversation(
    val uid: String,
    val username: String,
    val display: String,
    val av: String,
    val last: String,
    val ts: Long,
    val unread: Int,
    val fromMe: Boolean,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val request: Boolean = false,
    val note: Boolean = false
)

data class DmThreadData(val peer: DmPeer, val messages: List<DmMessage>, val typing: Boolean, val peerRead: Long, val blocked: Boolean, val ev: Long, val stars: Set<Long> = emptySet(), val disappear: Long = 0, val request: Boolean = false, val pending: Boolean = false)

/** Info grup Global Chat. myRole: "admin", "staff", atau kosong. */
data class ChatGroup(
    val name: String,
    val desc: String,
    val photoV: String,
    val hasPhoto: Boolean,
    val members: Int,
    val online: Int,
    val myRole: String,
    val canEdit: Boolean,
    val pins: List<ChatMessage> = emptyList()
)

data class ChatMember(val uid: String, val username: String, val display: String, val role: String, val online: Boolean, val lastSeen: Long = 0L)
