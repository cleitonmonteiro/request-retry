package io.github.cleitonmonteiro.requestretry.feature.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.cleitonmonteiro.requestretry.feature.migration.MigrationDemoActivity

@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenCreateOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Request Retry study case")
        Button(onClick = onOpenProfile, modifier = Modifier.padding(top = 24.dp)) {
            Text("Profile demo")
        }
        Button(onClick = onOpenOrders, modifier = Modifier.padding(top = 12.dp)) {
            Text("Orders demo")
        }
        Button(onClick = onOpenPicker, modifier = Modifier.padding(top = 12.dp)) {
            Text("Item picker demo")
        }
        Button(onClick = onOpenCreateOrder, modifier = Modifier.padding(top = 12.dp)) {
            Text("Create order demo")
        }
        // A plain Android intent, not a NavController.navigate call — this destination is
        // deliberately outside AppNavHost's NavHost, see MigrationDemoActivity's doc comment.
        Button(
            onClick = { context.startActivity(Intent(context, MigrationDemoActivity::class.java)) },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Legacy migration demo (standalone Activity)")
        }
    }
}
