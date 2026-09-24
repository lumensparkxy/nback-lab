package com.maswadkar.nback

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.ump.ConsentInformation

@Composable
internal fun PrivacyControls() {
    val activity = LocalActivity.current as? MainActivity
    var open by remember { mutableStateOf(false) }
    val runtime = activity?.adsRuntime
    runtime?.revision // Observe SDK changes; never store a consent boolean as authority.
    TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("privacy")) {
        Text(stringResource(R.string.privacy))
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.privacy)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.privacy_notice))
                if (runtime?.privacyError == true) {
                    Text(stringResource(R.string.privacy_unavailable))
                    TextButton(onClick = { open = false; activity.adSurface.retryPrivacy() }) {
                        Text(stringResource(R.string.privacy_retry))
                    }
                }
                if (runtime?.enabled == true && runtime.consent.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                    TextButton(onClick = { open = false; activity.adSurface.privacyOptions() }, modifier = Modifier.testTag("privacy_options")) {
                        Text(stringResource(R.string.privacy_options))
                    }
                }
                if (BuildConfig.PRIVACY_URL.isNotEmpty()) TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, BuildConfig.PRIVACY_URL.toUri())
                    try { activity?.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { /* Bundled notice remains readable. */ }
                }) { Text(stringResource(R.string.privacy_policy)) }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.privacy_close)) } })
}
