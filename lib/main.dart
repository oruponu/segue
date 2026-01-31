import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const SegueApp());
}

class SegueApp extends StatelessWidget {
  const SegueApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Segue',
      theme: ThemeData.dark(),
      home: const PlayerScreen(title: "Segue Player"),
    );
  }
}

class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key, required this.title});

  final String title;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  late AudioPlayer _player;
  String? _selectedDirectory;

  @override
  void initState() {
    super.initState();
    _player = AudioPlayer();
  }

  Future<void> _selectDirectory() async {
    String? selectedDirectory = await FilePicker.platform.getDirectoryPath();
    if (selectedDirectory == null) {
      return;
    }

    setState(() {
      _selectedDirectory = selectedDirectory;
    });

    await _loadAudioSources(selectedDirectory);
  }

  Future<void> _loadAudioSources(String path) async {
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
    final List<AudioSource> audioSources = files
        .where((file) => file.path.endsWith('.mp3'))
        .map((file) => AudioSource.uri(Uri.parse(file.path)))
        .toList();

    await _player.setAudioSources(audioSources);
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      floatingActionButton: FloatingActionButton(
        onPressed: _selectDirectory,
        child: const Icon(Icons.folder_open),
      ),
      body: SafeArea(
        child: SizedBox(
          width: double.infinity,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Text(_selectedDirectory ?? "フォルダを選択してください"),
              ),

              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    StreamBuilder<PlayerState>(
                      stream: _player.playerStateStream,
                      builder: (context, snapshot) {
                        final playerState = snapshot.data;
                        final processingState = playerState?.processingState;
                        final playing = playerState?.playing;

                        if (processingState == ProcessingState.loading ||
                            processingState == ProcessingState.buffering) {
                          return const CircularProgressIndicator();
                        } else if (playing != true) {
                          return IconButton(
                            icon: const Icon(Icons.play_arrow),
                            iconSize: 64.0,
                            onPressed: _player.play,
                          );
                        } else if (processingState !=
                            ProcessingState.completed) {
                          return IconButton(
                            icon: const Icon(Icons.pause),
                            iconSize: 64.0,
                            onPressed: _player.pause,
                          );
                        } else {
                          return IconButton(
                            icon: const Icon(Icons.replay),
                            iconSize: 64.0,
                            onPressed: () => _player.seek(Duration.zero),
                          );
                        }
                      },
                    ),

                    const SizedBox(height: 20),
                    IconButton(
                      icon: const Icon(Icons.skip_next),
                      iconSize: 48,
                      onPressed: () {
                        if (_player.hasNext) {
                          _player.seekToNext();
                        }
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
