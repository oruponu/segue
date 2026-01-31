import 'dart:io';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:permission_handler/permission_handler.dart';
import '../model/library_state.dart';
import '../providers/audio_player_provider.dart';

final libraryViewModelProvider =
    NotifierProvider<LibraryViewModel, LibraryState>(() {
      return LibraryViewModel();
    });

class LibraryViewModel extends Notifier<LibraryState> {
  @override
  LibraryState build() {
    final player = ref.read(audioPlayerProvider);
    player.sequenceStateStream.listen((sequenceState) {
      if (sequenceState.currentSource != null) {
        final metadata = sequenceState.currentSource?.tag as AudioMetadata?;
        state = state.copyWith(playingMetadata: metadata);
      }
    });
    return LibraryState();
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
    final player = ref.read(audioPlayerProvider);
    await player.seek(Duration.zero, index: index);
    player.play();
  }

  Future<void> _loadAudioSources(String path) async {
    final player = ref.read(audioPlayerProvider);

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

    List<FileSystemEntity> files = directory.listSync();
    List<AudioMetadata> metadataList = [];
    List<AudioSource> audioSources = [];
    for (var file in files) {
      final metadata = await readMetadata(File(file.path), getImage: true);
      metadataList.add(metadata);
      audioSources.add(AudioSource.uri(Uri.parse(file.path), tag: metadata));
    }

    if (audioSources.isNotEmpty) {
      await player.setAudioSources(audioSources);
    }

    state = state.copyWith(playlist: metadataList, isLoading: false);
  }
}
