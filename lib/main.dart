import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'view/player_screen.dart';

void main() {
  runApp(const ProviderScope(child: SegueApp()));
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
