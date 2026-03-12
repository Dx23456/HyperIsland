import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HyperIsland Test',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.orange),
        useMaterial3: true,
      ),
      home: const HyperIslandTestPage(),
    );
  }
}

class HyperIslandTestPage extends StatefulWidget {
  const HyperIslandTestPage({super.key});

  @override
  State<HyperIslandTestPage> createState() => _HyperIslandTestPageState();
}

class _HyperIslandTestPageState extends State<HyperIslandTestPage> {
  static const platform = MethodChannel('com.example.hyperisland/test');

  String _status = '准备就绪';
  double _progress = 0.0;
  Timer? _timer;

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _sendTestNotification(String type) async {
    try {
      bool success;
      switch (type) {
        case 'progress':
          success = await platform.invokeMethod('showProgress', {
            'title': '下载测试',
            'fileName': 'test_file.apk',
            'progress': _progress.toInt(),
            'speed': '5.2 MB/s',
            'remainingTime': '00:05',
          });
          break;
        case 'complete':
          success = await platform.invokeMethod('showComplete', {
            'title': '下载完成',
            'fileName': 'test_file.apk',
          });
          break;
        case 'failed':
          success = await platform.invokeMethod('showFailed', {
            'title': '下载失败',
            'fileName': 'test_file.apk',
            'error': '网络连接超时',
          });
          break;
        case 'indeterminate':
          success = await platform.invokeMethod('showIndeterminate', {
            'title': '准备中',
            'content': '正在连接服务器...',
          });
          break;
        case 'custom':
          success = await platform.invokeMethod('showCustom', {
            'type': 'custom_notification',
            'title': '自定义通知',
            'content': '这是一个自定义的灵动岛通知',
            'icon': 'android.R.drawable.ic_dialog_info',
          });
          break;
        default:
          success = false;
      }

      setState(() {
        _status = success ? '通知已发送' : '发送失败';
      });
    } on PlatformException catch (e) {
      setState(() {
        _status = '错误: ${e.message}';
      });
    }
  }

  void _startProgressDemo() {
    _progress = 0.0;
    _timer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
      setState(() {
        _progress += 5.0;
        if (_progress >= 100) {
          _progress = 100;
          timer.cancel();
          _sendTestNotification('complete');
        } else {
          _sendTestNotification('progress');
        }
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('HyperIsland 测试'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    const Text(
                      '状态',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(_status),
                    const SizedBox(height: 16),
                    if (_progress > 0 && _progress < 100)
                      Column(
                        children: [
                          LinearProgressIndicator(
                            value: _progress / 100,
                          ),
                          const SizedBox(height: 8),
                          Text('${_progress.toInt()}%'),
                        ],
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _startProgressDemo,
              icon: const Icon(Icons.play_arrow),
              label: const Text('开始进度演示'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () => _sendTestNotification('indeterminate'),
              icon: const Icon(Icons.hourglass_empty),
              label: const Text('显示不确定进度'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () => _sendTestNotification('complete'),
              icon: const Icon(Icons.check_circle),
              label: const Text('显示下载完成'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () => _sendTestNotification('failed'),
              icon: const Icon(Icons.error),
              label: const Text('显示下载失败'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () => _sendTestNotification('custom'),
              icon: const Icon(Icons.notifications),
              label: const Text('发送自定义通知'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
              ),
            ),
            const SizedBox(height: 24),
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '说明',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '此页面仅用于测试是否支持超级岛。\n\n'
                      '点击上方按钮可以发送不同类型的岛通知。\n\n'
                      '实际使用请在LSPosed管理器中激活使用',
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
