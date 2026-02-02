import 'package:flutter/material.dart';
import 'library_screen.dart';

class MainScreen extends StatelessWidget {
  const MainScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: const LibraryScreen());
  }
}
