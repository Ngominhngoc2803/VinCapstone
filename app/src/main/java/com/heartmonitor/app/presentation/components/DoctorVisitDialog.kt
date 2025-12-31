package com.heartmonitor.app.presentation.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.heartmonitor.app.domain.model.DoctorInfo
import com.heartmonitor.app.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DoctorVisitInput(
    val doctorName: String = "",
    val clinicName: String = "",
    val visitDate: LocalDate = LocalDate.now(),
    val doctorNote: String = "",
    val diagnosis: String = "",
    val recommendations: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorVisitDialog(
    isVisible: Boolean,
    existingDoctors: List<DoctorInfo> = emptyList(),
    initialData: DoctorVisitInput = DoctorVisitInput(),
    onDismiss: () -> Unit,
    onSave: (DoctorVisitInput) -> Unit
) {
    if (!isVisible) return

    var doctorName by remember { mutableStateOf(initialData.doctorName) }
    var clinicName by remember { mutableStateOf(initialData.clinicName) }
    var visitDate by remember { mutableStateOf(initialData.visitDate) }
    var doctorNote by remember { mutableStateOf(initialData.doctorNote) }
    var diagnosis by remember { mutableStateOf(initialData.diagnosis) }
    var recommendations by remember { mutableStateOf(initialData.recommendations) }
    var showDoctorDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add Doctor Visit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Doctor Name with dropdown
                    Column {
                        Text(
                            text = "Doctor Name *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = showDoctorDropdown && existingDoctors.isNotEmpty(),
                            onExpandedChange = { showDoctorDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = doctorName,
                                onValueChange = {
                                    doctorName = it
                                    showDoctorDropdown = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                placeholder = { Text("e.g., Dr. John Smith") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = OrangePrimary
                                    )
                                },
                                trailingIcon = {
                                    if (existingDoctors.isNotEmpty()) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDoctorDropdown)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OrangePrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                singleLine = true
                            )

                            if (existingDoctors.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = showDoctorDropdown,
                                    onDismissRequest = { showDoctorDropdown = false }
                                ) {
                                    existingDoctors
                                        .filter { it.name.contains(doctorName, ignoreCase = true) || doctorName.isEmpty() }
                                        .forEach { doctor ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = doctor.name,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = doctor.address,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    doctorName = doctor.name
                                                    // Auto-fill clinic if available
                                                    if (clinicName.isEmpty()) {
                                                        clinicName = doctor.address.split(",").firstOrNull() ?: ""
                                                    }
                                                    showDoctorDropdown = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = OrangePrimary
                                                    )
                                                }
                                            )
                                        }
                                }
                            }
                        }
                    }

                    // Clinic/Hospital Name
                    Column {
                        Text(
                            text = "Clinic/Hospital Name *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clinicName,
                            onValueChange = { clinicName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., City Heart Center") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocalHospital,
                                    contentDescription = null,
                                    tint = OrangePrimary
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            singleLine = true
                        )
                    }

                    // Visit Date
                    Column {
                        Text(
                            text = "Visit Date *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            visitDate = LocalDate.of(year, month + 1, dayOfMonth)
                                        },
                                        visitDate.year,
                                        visitDate.monthValue - 1,
                                        visitDate.dayOfMonth
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = OrangePrimary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = visitDate.format(dateFormatter),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Select date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Diagnosis
                    Column {
                        Text(
                            text = "Diagnosis",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = diagnosis,
                            onValueChange = { diagnosis = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., Normal sinus rhythm") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = OrangePrimary
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            singleLine = true
                        )
                    }

                    // Doctor's Note
                    Column {
                        Text(
                            text = "Doctor's Note",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = doctorNote,
                            onValueChange = { doctorNote = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("Enter notes from your doctor...") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            maxLines = 5
                        )
                    }

                    // Recommendations
                    Column {
                        Text(
                            text = "Recommendations",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = recommendations,
                            onValueChange = { recommendations = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            placeholder = { Text("e.g., Follow up in 3 months...") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            maxLines = 4
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(
                                DoctorVisitInput(
                                    doctorName = doctorName,
                                    clinicName = clinicName,
                                    visitDate = visitDate,
                                    doctorNote = doctorNote,
                                    diagnosis = diagnosis,
                                    recommendations = recommendations
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = doctorName.isNotBlank() && clinicName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorVisitInfoCard(
    doctorName: String?,
    clinicName: String?,
    visitDate: LocalDate?,
    doctorNote: String?,
    diagnosis: String?,
    recommendations: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVisitInfo = doctorName != null || clinicName != null
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasVisitInfo)
                GreenGood.copy(alpha = 0.1f)
            else
                YellowCaution.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasVisitInfo)
                            Icons.Default.Verified
                        else
                            Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasVisitInfo) GreenGood else YellowCaution,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasVisitInfo) "Clinic Verified" else "Not Verified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasVisitInfo) GreenGood else YellowCaution
                    )
                }

                TextButton(onClick = onEditClick) {
                    Icon(
                        imageVector = if (hasVisitInfo) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasVisitInfo) "Edit" else "Add Visit")
                }
            }

            if (hasVisitInfo) {
                Spacer(modifier = Modifier.height(12.dp))

                // Doctor info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = doctorName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Clinic info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = clinicName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Visit date
                if (visitDate != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = visitDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Diagnosis
                if (!diagnosis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        Text(
                            text = "Diagnosis",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = diagnosis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Doctor's Note
                if (!doctorNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        Text(
                            text = "Doctor's Note",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = doctorNote,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Recommendations
                if (!recommendations.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        Text(
                            text = "Recommendations",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = recommendations,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add your doctor visit details to verify this recording",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}