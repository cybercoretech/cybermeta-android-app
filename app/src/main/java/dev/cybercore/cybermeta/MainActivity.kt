package dev.cybercore.cybermeta

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppModel by viewModels()

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        vm.open(uris, fromShare = false)
    }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.open(uris, fromShare = false)
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermission()
        if (!it) vm.note("Not allowed. You can still clean photos; the report just can't show their GPS.")
    }
    private val writeRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) vm.replaceGranted() else vm.note("Originals left as they were.")
    }

    private val actions = object : Actions {
        override fun pickPhotos() = pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        override fun pickFiles() = pickFiles.launch(arrayOf("image/*"))
        override fun askLocationPermission() = locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        override fun share() = vm.share()

        override fun replaceOriginals() {
            val uris = vm.replaceable()
            if (uris.isEmpty()) return
            // Android's own prompt: the user approves overwriting these exact photos.
            val pending = MediaStore.createWriteRequest(contentResolver, uris)
            writeRequest.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }

        override fun openMap(lat: Double, lon: Double) {
            val geo = "geo:$lat,$lon?q=$lat,$lon".toUri()
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                .onFailure { vm.note("No maps app installed.") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        OfflineGeocoder.init(this)
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.pendingShare.filterNotNull().collect { uris ->
                    vm.pendingShare.value = null
                    openShareSheet(uris)
                }
            }
        }
        setContent {
            val s = vm.state.collectAsStateWithLifecycle().value
            CybermetaTheme(s.palette) { App(s, vm, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Photos shared in from Gallery, Photos, a file manager, or Quick Settings tile. */
    private fun handle(intent: Intent?) {
        if (intent?.action == CleanTileService.ACTION_CLEAN_LATEST) {
            cleanLatestPhoto()
            return
        }
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        vm.open(uris, fromShare = true)
    }

    private fun cleanLatestPhoto() {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val uri = runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                } else null
            }
        }.getOrNull()

        if (uri != null) {
            vm.open(listOf(uri), fromShare = false)
        } else {
            vm.note("No camera photos found.")
        }
    }

    private fun openShareSheet(uris: List<Uri>) {
        if (uris.isEmpty()) {
            vm.note("Nothing to share.")
            return
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = "image/*"
            clipData = ClipData.newRawUri(null, uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_clean)))
    }
}
