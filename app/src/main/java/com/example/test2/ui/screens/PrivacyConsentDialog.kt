package com.example.test2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PrivacyConsentDialog(
    onEnableVault: () -> Unit,
    onKeepStandard: () -> Unit
) {
    Dialog(
        onDismissRequest = onKeepStandard,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFEFF6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Bảo mật",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Bảo vệ riêng tư cho ảnh chụp màn hình?",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bạn có muốn giữ các ảnh chụp màn hình của mình riêng tư và an toàn không?",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: BẬT BẢO VỆ
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0FDF4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFDCFCE7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "BẬT BẢO VỆ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ảnh chụp màn hình chỉ xem được trong ứng dụng này và không xuất hiện ở Bộ sưu tập hay nơi khác. Dùng khóa máy (vân tay, khuôn mặt hoặc mã PIN) để mở ứng dụng.",
                                fontSize = 12.sp,
                                color = Color(0xFF166534),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: KHÔNG BẬT
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFF1F5F9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "KHÔNG BẬT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ảnh vẫn hiển thị bình thường ở Bộ sưu tập như cũ và không cần mở khóa khi vào ứng dụng.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Bạn có thể thay đổi lại lựa chọn này bất kỳ lúc nào trong phần Cài đặt.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons (Stacked vertically to guarantee full readability on all screen sizes)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onEnableVault,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Bật bảo vệ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onKeepStandard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF475569)
                        )
                    ) {
                        Text(
                            text = "Không, giữ như cũ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
