package io.github.cleitonmonteiro.requestretry.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    }
}
