import 'dart:io';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:audio_service/audio_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:permission_handler/permission_handler.dart';
import '../model/library_state.dart';
import '../providers/audio_handler_provider.dart';

final libraryViewModelProvider =
    NotifierProvider<LibraryViewModel, LibraryState>(() {
      return LibraryViewModel();
    });

class LibraryViewModel extends Notifier<LibraryState> {
  @override
  LibraryState build() {
    ref.listen(mediaItemProvider, (previous, next) {
      next.whenData((item) {
        state = state.copyWith(
          playingMetadata: item != null
              ? AudioMetadata(
                  file: File(item.id),
                  title: item.title,
                  album: item.album,
                  artist: item.artist,
                  duration: item.duration,
                )
              : null,
        );
      });
    });

    final initialMediaItem = ref.read(mediaItemProvider);
    return LibraryState(
      playingMetadata: initialMediaItem.maybeWhen(
        orElse: () => null,
        data: (item) => item != null
            ? AudioMetadata(
                file: File(item.id),
                title: item.title,
                album: item.album,
                artist: item.artist,
                duration: item.duration,
              )
            : null,
      ),
    );
  }

  Future<void> selectDirectory() async {
    String? selectedDirectory = await FilePicker.platform.getDirectoryPath();
    if (selectedDirectory == null) {
      return;
    }

    state = state.copyWith(
      selectedDirectory: selectedDirectory,
      isLoading: true,
    );
    await _loadAudioSources(selectedDirectory);
    state = state.copyWith(isLoading: false);
  }

  Future<void> playItem(int index) async {
    final handler = ref.read(audioHandlerProvider);
    await handler.skipToQueueItem(index);
    handler.play();
  }

  Future<void> _loadAudioSources(String path) async {
    final handler = ref.read(audioHandlerProvider);

    var audioStatus = await Permission.audio.status;
    if (!audioStatus.isGranted) {
      audioStatus = await Permission.audio.request();
    }

    if (!audioStatus.isGranted) {
      var storageStatus = await Permission.storage.request();
      if (!storageStatus.isGranted) {
        return;
      }
    }

    final directory = Directory(path);
    if (!await directory.exists()) {
      return;
    }

    final metadataList = directory
        .listSync()
        .map((file) => readMetadata(File(file.path), getImage: true))
        .toList();
    final mediaItems = metadataList
        .map(
          (metadata) => MediaItem(
            id: metadata.file.path,
            title: metadata.title ?? "Unknown Title",
            album: metadata.album,
            artist: metadata.artist,
            duration: metadata.duration,
          ),
        )
        .toList();
    await handler.updateQueue(mediaItems);

    state = state.copyWith(playlist: metadataList, isLoading: false);
  }
}
