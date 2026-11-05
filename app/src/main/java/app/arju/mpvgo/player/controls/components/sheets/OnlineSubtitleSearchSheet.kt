package app.arju.mpvgo.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.core.presentation.components.PlayerSheet
import app.arju.mpvgo.core.theme.spacing
import app.arju.mpvgo.subtitles.MovieHash
import app.arju.mpvgo.subtitles.OnlineSubtitle
import kotlinx.coroutines.launch

@Composable
fun OnlineSubtitleSearchSheet(
  mediaTitle: String,
  filePath: String?,
  onSubtitleLoaded: (android.net.Uri) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val repository = org.koin.compose.koinInject<app.arju.mpvgo.subtitles.SubtitleRepository>()
  val coroutineScope = rememberCoroutineScope()
  var isSearching by remember { mutableStateOf(false) }
  var searchResults by remember { mutableStateOf<List<OnlineSubtitle>>(emptyList()) }
  var downloadingId by remember { mutableStateOf<String?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current

  // Derive initial search query from media title or file name
  val initialQuery = remember(mediaTitle, filePath) {
    if (mediaTitle.isNotBlank()) mediaTitle
    else if (!filePath.isNullOrBlank()) java.io.File(filePath).nameWithoutExtension
    else ""
  }

  fun performSearch(query: String, lang: String?) {
    coroutineScope.launch {
      isSearching = true
      try {
        val file = filePath?.let { java.io.File(it) }
        val hash = if (file?.exists() == true) MovieHash.compute(file) else null
        val size = if (file?.exists() == true) file.length() else null
        val searchQuery = app.arju.mpvgo.subtitles.SubtitleSearchQuery(
          title = query,
          movieHash = hash,
          fileSize = size,
          languages = if (lang != null) listOf(lang) else emptyList(),
        )
        searchResults = repository.search(searchQuery)
      } catch (e: Exception) {
        searchResults = emptyList()
      } finally {
        isSearching = false
      }
    }
  }

  LaunchedEffect(initialQuery) {
    if (initialQuery.isNotBlank() || !filePath.isNullOrBlank()) {
      performSearch(initialQuery, null)
    }
  }

  OnlineSubtitleSearchContent(
    initialQuery = initialQuery,
    isSearching = isSearching,
    searchResults = searchResults,
    downloadingId = downloadingId,
    onSearch = { query, lang -> performSearch(query, lang) },
    onDownload = { subtitle ->
      coroutineScope.launch {
        downloadingId = subtitle.id
        try {
          val savedFile = repository.downloadAndSave(
            subtitle = subtitle,
            videoPath = filePath,
          )
          if (savedFile != null) {
            onSubtitleLoaded(android.net.Uri.fromFile(savedFile))
            onDismissRequest()
          } else {
            android.widget.Toast.makeText(context, "Failed to download subtitle", android.widget.Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        } finally {
          downloadingId = null
        }
      }
    },
    onDismissRequest = onDismissRequest,
    modifier = modifier,
  )
}

@Composable
fun OnlineSubtitleSearchContent(
  initialQuery: String,
  isSearching: Boolean,
  searchResults: List<OnlineSubtitle>,
  downloadingId: String?,
  onSearch: (String, String?) -> Unit,
  onDownload: (OnlineSubtitle) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember(initialQuery) { mutableStateOf(initialQuery) }
  var selectedLang by remember { mutableStateOf<String?>(null) }
  val keyboardController = LocalSoftwareKeyboardController.current

  val languages = remember {
    listOf(
      null to "All",
      "en" to "English",
      "hi" to "Hindi",
      "es" to "Spanish",
      "fr" to "French",
      "de" to "German",
      "it" to "Italian",
      "pt" to "Portuguese",
      "ru" to "Russian",
      "ar" to "Arabic",
      "ja" to "Japanese",
      "ko" to "Korean",
      "zh" to "Chinese",
    )
  }

  val filteredResults = remember(searchResults, selectedLang) {
    if (selectedLang == null) searchResults
    else searchResults.filter { it.languageCode.equals(selectedLang, ignoreCase = true) }
  }

  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(bottom = MaterialTheme.spacing.medium)
    ) {
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(
            "Online Subtitles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Search bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search title, release, or movie...") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
          onSearch = {
            keyboardController?.hide()
            onSearch(searchQuery, selectedLang)
          }
        ),
        shape = RoundedCornerShape(12.dp),
      )

      Spacer(Modifier.height(8.dp))

      // Language filter chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = MaterialTheme.spacing.medium, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        languages.forEach { (code, label) ->
          val isSelected = selectedLang == code
          FilterChip(
            selected = isSelected,
            onClick = {
              selectedLang = code
              if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                onSearch(searchQuery, code)
              }
            },
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
          )
        }
      }

      Spacer(Modifier.height(4.dp))

      // Results or Loading or Empty
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 120.dp, max = 360.dp),
        contentAlignment = Alignment.Center,
      ) {
        when {
          isSearching -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(24.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(36.dp))
              Spacer(Modifier.height(12.dp))
              Text("Searching online subtitles...", style = MaterialTheme.typography.bodyMedium)
            }
          }
          filteredResults.isEmpty() -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(24.dp),
            ) {
              Icon(
                Icons.Default.SubtitlesOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(8.dp))
              Text(
                "No subtitles found",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              Text(
                "Try searching with a simpler title or different language",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          else -> {
            LazyColumn(
              modifier = Modifier.fillMaxWidth(),
              contentPadding = PaddingValues(vertical = 4.dp),
            ) {
              items(filteredResults, key = { it.id }) { sub ->
                val isDownloadingThis = downloadingId == sub.id
                OnlineSubtitleRow(
                  subtitle = sub,
                  isDownloading = isDownloadingThis,
                  onDownload = { onDownload(sub) },
                )
                HorizontalDivider(
                  modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OnlineSubtitleRow(
  subtitle: OnlineSubtitle,
  isDownloading: Boolean,
  onDownload: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = !isDownloading, onClick = onDownload)
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (subtitle.isExactMatch) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 6.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.width(2.dp))
              Text(
                "Exact match",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
        Text(
          subtitle.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(Modifier.height(2.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          subtitle.language.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
          subtitle.provider,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle.downloadCount > 0) {
          Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          Text(
            "${subtitle.downloadCount} dl",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (subtitle.rating > 0f) {
          Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          Text(
            "★ ${String.format(java.util.Locale.US, "%.1f", subtitle.rating)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE5A000),
          )
        }
      }
    }

    Spacer(Modifier.width(8.dp))

    if (isDownloading) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp).padding(2.dp),
        strokeWidth = 2.dp,
      )
    } else {
      IconButton(onClick = onDownload) {
        Icon(
          Icons.Default.Download,
          contentDescription = "Download subtitle",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
