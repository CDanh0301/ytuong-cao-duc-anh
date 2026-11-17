package com.example.test2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLockScreen(
    onUnlockClick: () -> Unit,
    onBackPressed: () -> Unit = {}
) {
    // Intercept Back press while locked so user cannot bypass or back into previous screens
    BackHandler(enabled = true) {
        onBackPressed()
    }

    // Automatically trigger biometric/screen lock prompt when lock screen appears
    LaunchedEffect(Unit) {
        onUnlockClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Actively consume all touch events outside the unlock button
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Lock Icon Badge
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF1E3A8A).copy(alpha = 0.5f), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Đã khóa",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Két ảnh bảo mật",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ứng dụng đang được khóa để bảo vệ các ảnh chụp màn hình riêng tư của bạn.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Mở khóa bằng khóa máy",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
