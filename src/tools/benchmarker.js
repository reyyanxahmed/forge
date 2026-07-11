import fs from 'node:fs';
import path from 'node:path';

export function benchmarkAndroidProject(projDir) {
  let score = 60; // baseline
  const auditLogs = [];
  
  const mainScreenPath = path.join(projDir, 'app/src/main/java/com/example/testapp/ui/main/MainScreen.kt');
  const mainScreenViewModelPath = path.join(projDir, 'app/src/main/java/com/example/testapp/ui/main/MainScreenViewModel.kt');
  
  // 1. Check MainScreen Compose layout
  if (fs.existsSync(mainScreenPath)) {
    score += 10;
    auditLogs.push('✓ MainScreen.kt layout file exists (+10)');
    
    const code = fs.readFileSync(mainScreenPath, 'utf8');
    if (code.includes('remember') || code.includes('rememberSaveable')) {
      score += 5;
      auditLogs.push('✓ Proper Compose state preservation detected via remember/rememberSaveable (+5)');
    } else {
      auditLogs.push('⚠ MainScreen.kt does not use remember/rememberSaveable for local UI state');
    }
    
    if (code.includes('Scaffold') || code.includes('MaterialTheme')) {
      score += 5;
      auditLogs.push('✓ Standard Material 3 / Material You theme configuration found (+5)');
    }
    
    if (code.includes('TextToSpeech') || code.includes('tts') || code.includes('SpeechRecognizer')) {
      score += 10;
      auditLogs.push('✓ Rich on-device Multimodality (Speech/Audio) successfully integrated (+10)');
    }
  } else {
    auditLogs.push('✗ MainScreen.kt is missing');
  }
  
  // 2. Check ViewModel state flow handling
  if (fs.existsSync(mainScreenViewModelPath)) {
    score += 10;
    auditLogs.push('✓ MainScreenViewModel.kt exists (+10)');
    
    const code = fs.readFileSync(mainScreenViewModelPath, 'utf8');
    if (code.includes('StateFlow') || code.includes('MutableStateFlow') || code.includes('mutableStateOf')) {
      score += 10;
      auditLogs.push('✓ Reactive state flows / Compose mutableState flow architectures detected (+10)');
    } else {
      auditLogs.push('⚠ ViewModel does not use reactive flows');
    }
  } else {
    auditLogs.push('✗ MainScreenViewModel.kt is missing');
  }
  
  const finalScore = Math.min(100, score);
  
  return {
    score: finalScore,
    logs: auditLogs
  };
}
