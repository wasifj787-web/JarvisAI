package com.jarvis.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var deleteAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HudTopBar("History / Notes", onBack) {
            if (vm.notes.isNotEmpty()) {
                IconButton(onClick = { deleteAll = true }) { Icon(Icons.Default.DeleteSweep, "Delete all", tint = Danger) }
            }
        }
        if (vm.notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No saved notes yet.\nScan an object with the camera and tap “Save note”.",
                    color = CyanDim, textAlign = TextAlign.Center, lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.notes, key = { it.id }) { n ->
                    HudCard(Modifier.fillMaxWidth()) {
                        Text(n.name, color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(n.savedAt)),
                            color = CyanDim, fontSize = 11.sp
                        )
                        NoteLine("BRAND / MODEL", n.brand)
                        NoteLine("ABOUT", n.description)
                        NoteLine("USES", n.uses)
                        NoteLine("FEATURES", n.features)
                        NoteLine("PRICE (PKR)", n.price)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { vm.speakNote(n) }) { Icon(Icons.Default.VolumeUp, "Speak", tint = Cyan) }
                            IconButton(onClick = { deleteId = n.id }) { Icon(Icons.Default.Delete, "Delete", tint = Danger) }
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteNote(id); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
    if (deleteAll) {
        AlertDialog(
            onDismissRequest = { deleteAll = false },
            title = { Text("Delete all notes?") },
            text = { Text("All ${vm.notes.size} saved notes will be removed.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllNotes(); deleteAll = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NoteLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, color = CyanDim, fontSize = 10.sp, letterSpacing = 2.sp)
        Text(value, color = TextLight, fontSize = 15.sp, lineHeight = 22.sp)
    }
}
