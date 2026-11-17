package com.example.test2.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun EnableVaultScopeDialog(
    onConfirm: (vaultAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var vaultAll by remember { mutableStateOf(true) }

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
                        .size(56.dp)
                        .background(Color(0xFFEFF6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Bảo vệ",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Phạm vi bảo vệ ảnh",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Chọn những ảnh bạn muốn đưa vào Két bảo mật:",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Card Option 1: Tất cả ảnh
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vaultAll = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (vaultAll) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (vaultAll) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = vaultAll,
                            onClick = { vaultAll = true },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF2563EB),
                                unselectedColor = Color(0xFF94A3B8)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Tất cả ảnh",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vaultAll) Color(0xFF1E40AF) else Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Chuyển cả ảnh cũ vào Két và ẩn khỏi máy",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card Option 2: Chỉ ảnh mới
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vaultAll = false },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!vaultAll) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (!vaultAll) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !vaultAll,
                            onClick = { vaultAll = false },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF2563EB),
                                unselectedColor = Color(0xFF94A3B8)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Chỉ ảnh mới",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!vaultAll) Color(0xFF1E40AF) else Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Giữ nguyên ảnh cũ ngoài máy, chỉ bảo vệ từ giờ",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Actions: Bật bảo vệ & Hủy
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onConfirm(vaultAll) },
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
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Hủy",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
