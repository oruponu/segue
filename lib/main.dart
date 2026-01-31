import 'dart:io';
import 'dart:typed_data';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
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
    List<AudioSource> audioSources = [];
    for (var file in files) {
      final metadata = await readMetadata(File(file.path), getImage: true);
      audioSources.add(AudioSource.uri(Uri.parse(file.path), tag: metadata));
    }

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
                    StreamBuilder<SequenceState?>(
                      stream: _player.sequenceStateStream,
                      builder: (context, snapshot) {
                        final state = snapshot.data;
                        if (state == null || state.sequence.isEmpty) {
                          return const SizedBox();
                        }

                        final currentSource = state.currentSource;
                        if (currentSource == null) {
                          return const SizedBox();
                        }

                        final metadata = currentSource.tag as AudioMetadata?;
                        Uint8List? albumArt;
                        if (metadata != null && metadata.pictures.isNotEmpty) {
                          albumArt = metadata.pictures.first.bytes;
                        }

                        return Column(
                          children: [
                            Container(
                              width: 250,
                              height: 250,
                              decoration: BoxDecoration(
                                color: Colors.grey[800],
                                borderRadius: BorderRadius.circular(12),
                                boxShadow: const [
                                  BoxShadow(
                                    color: Colors.black45,
                                    blurRadius: 10,
                                    offset: Offset(0, 5),
                                  ),
                                ],
                              ),

                              child: albumArt != null
                                  ? ClipRRect(
                                      borderRadius: BorderRadius.circular(12),
                                      child: Image.memory(
                                        albumArt,
                                        fit: BoxFit.cover,
                                      ),
                                    )
                                  : const Icon(
                                      Icons.music_note,
                                      size: 100,
                                      color: Colors.white24,
                                    ),
                            ),
                            const SizedBox(height: 30),
                            Text(
                              metadata?.title ?? "Unknown Title",
                              style: const TextStyle(
                                fontSize: 24,
                                fontWeight: FontWeight.bold,
                              ),
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              metadata?.artist ?? "Unknown Artist",
                              style: const TextStyle(
                                fontSize: 18,
                                color: Colors.white70,
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ],
                        );
                      },
                    ),

                    const SizedBox(height: 40),

                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.shuffle),
                          onPressed: () {
                            // TODO: ランダム再生の実装
                          },
                        ),

                        IconButton(
                          icon: const Icon(Icons.skip_previous),
                          iconSize: 48,
                          onPressed: () {
                            if (_player.hasPrevious) {
                              _player.seekToPrevious();
                            } else {
                              _player.seek(Duration.zero);
                            }
                          },
                        ),

                        StreamBuilder<PlayerState>(
                          stream: _player.playerStateStream,
                          builder: (context, snapshot) {
                            final playerState = snapshot.data;
                            final processingState =
                                playerState?.processingState;
                            final playing = playerState?.playing;

                            if (playing != true) {
                              return IconButton(
                                icon: const Icon(Icons.play_circle_fill),
                                iconSize: 64,
                                onPressed: _player.play,
                              );
                            } else if (processingState !=
                                ProcessingState.completed) {
                              return IconButton(
                                icon: const Icon(Icons.pause_circle_filled),
                                iconSize: 64,
                                onPressed: _player.pause,
                              );
                            } else {
                              return IconButton(
                                icon: const Icon(Icons.replay_circle_filled),
                                iconSize: 64,
                                onPressed: () => _player.seek(Duration.zero),
                              );
                            }
                          },
                        ),

                        IconButton(
                          icon: const Icon(Icons.skip_next),
                          iconSize: 48,
                          onPressed: _player.hasNext
                              ? _player.seekToNext
                              : null,
                        ),

                        IconButton(
                          icon: const Icon(Icons.repeat),
                          onPressed: () {
                            // TODO: リピート再生の実装
                          },
                        ),
                      ],
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

class PositionData {
  final Duration position;
  final Duration bufferedPosition;
  final Duration duration;

  PositionData(this.position, this.bufferedPosition, this.duration);
}
