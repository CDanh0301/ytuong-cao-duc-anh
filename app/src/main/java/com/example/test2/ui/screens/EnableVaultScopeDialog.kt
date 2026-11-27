package com.example.test2.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test2.ui.theme.AppColors

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
            color = AppColors.SurfaceAlabaster,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AppColors.ForestTealSoft, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Bảo vệ",
                        tint = AppColors.ForestTeal,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Phạm vi bảo vệ ảnh",
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.ForestTeal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Chọn những ảnh bạn muốn đưa vào Két bảo mật:",
                    fontSize = 13.sp,
                    color = AppColors.WarmTaupeText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vaultAll = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (vaultAll) AppColors.ForestTealSoft else AppColors.CanvasCream
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (vaultAll) AppColors.ForestTeal else AppColors.BorderSand
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
                                selectedColor = AppColors.ForestTeal,
                                unselectedColor = AppColors.WarmMutedText
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Tất cả ảnh",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vaultAll) AppColors.ForestTeal else AppColors.DeepPineText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Chuyển cả ảnh cũ vào Két và ẩn khỏi máy",
                                fontSize = 12.sp,
                                color = AppColors.WarmTaupeText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vaultAll = false },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!vaultAll) AppColors.ForestTealSoft else AppColors.CanvasCream
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (!vaultAll) AppColors.ForestTeal else AppColors.BorderSand
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
                                selectedColor = AppColors.ForestTeal,
                                unselectedColor = AppColors.WarmMutedText
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Chỉ ảnh mới",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!vaultAll) AppColors.ForestTeal else AppColors.DeepPineText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Giữ nguyên ảnh cũ ngoài máy, chỉ bảo vệ từ giờ",
                                fontSize = 12.sp,
                                color = AppColors.WarmTaupeText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onConfirm(vaultAll) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.ForestTeal,
                            contentColor = AppColors.SurfaceAlabaster
                        )
                    ) {
                        Text(text = "Bật bảo vệ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "Hủy", fontSize = 14.sp, color = AppColors.WarmTaupeText)
                    }
                }
            }
        }
    }
}
