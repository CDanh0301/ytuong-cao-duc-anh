package com.example.test2.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
fun DisableVaultConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    photoCount: Int
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFFFEF3C7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Khôi phục",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Khôi phục ảnh về thiết bị?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (photoCount > 0) {
                        "Tất cả $photoCount ảnh chụp màn hình đang được bảo vệ sẽ được giải mã và chuyển trả an toàn về Thư viện ảnh (Bộ sưu tập) của thiết bị để bạn có thể xem ở các ứng dụng khác.\n\nSau khi chuyển thành công, tính năng Két bảo mật sẽ được tắt."
                    } else {
                        "Bạn có chắc muốn tắt chế độ Két bảo mật không? Sau khi tắt, ứng dụng sẽ không yêu cầu mở khóa máy khi truy cập."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onConfirm,
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
                            text = if (photoCount > 0) "Khôi phục và tắt bảo vệ" else "Tắt bảo mật",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Hủy, giữ nguyên",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreProgressDialog(
    current: Int,
    total: Int
) {
    Dialog(
        onDismissRequest = { /* Cannot dismiss during active batch restore */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF2563EB),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Đang khôi phục ảnh về thiết bị...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (total > 0) "Đang xử lý: $current / $total ảnh" else "Đang chuẩn bị...",
                    fontSize = 13.sp,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.SemiBold
                )

                if (total > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = current.toFloat() / total.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Vui lòng giữ ứng dụng mở cho đến khi hoàn tất.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
