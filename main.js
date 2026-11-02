// MKVCast Desktop - ventana de la aplicación (Electron). El servidor corre dentro de este mismo proceso.
const { app, BrowserWindow, Tray, Menu, nativeImage, dialog, ipcMain } = require('electron');
const path = require('path');

let win = null, tray = null, quitting = false, port = 8099;

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', showWindow);
  app.whenReady().then(start);
}

function iconImage() {
  const p = path.join(__dirname, 'build', 'icon.png');
  const img = nativeImage.createFromPath(p);
  return img.isEmpty() ? undefined : img;
}

function showWindow() {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}

async function start() {
  Menu.setApplicationMenu(null);
  let srv;
  try {
    srv = require('./server.js');
    port = srv.PORT;
    await srv.ready;
  } catch (e) {
    dialog.showErrorBox('MKVCast',
      'No se pudo iniciar el servidor en el puerto ' + port + '.\n\n' +
      'Si tenés abierto otro MKVCast (por ejemplo mkvcast.exe o "node server.js"), cerralo y volvé a abrir esta aplicación.\n\n' +
      (e && e.message ? e.message : ''));
    app.quit();
    return;
  }

  win = new BrowserWindow({
    width: 1240,
    height: 780,
    minWidth: 980,
    minHeight: 620,
    backgroundColor: '#0b0d12',
    title: 'MKVCast',
    icon: iconImage(),
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });
  win.once('ready-to-show', function() { win.show(); });
  win.loadURL('http://127.0.0.1:' + port + '/desktop');

  // Cerrar la ventana no corta la transmisión: queda en la bandeja del sistema
  win.on('close', function(e) {
    if (!quitting) { e.preventDefault(); win.hide(); }
  });

  try {
    const img = iconImage();
    tray = new Tray(img ? img.resize({ width: 18, height: 18 }) : nativeImage.createEmpty());
    tray.setToolTip('MKVCast');
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Abrir MKVCast', click: showWindow },
      { type: 'separator' },
      { label: 'Salir', click: function() { quitting = true; app.quit(); } }
    ]));
    tray.on('click', showWindow);
  } catch (e) { /* sin bandeja: se puede cerrar igual desde la ventana */ }
}

app.on('before-quit', function() { quitting = true; });
app.on('window-all-closed', function() { /* queda en la bandeja */ });

ipcMain.handle('pick-folder', async function() {
  const r = await dialog.showOpenDialog(win, { title: 'Elegí una carpeta con videos', properties: ['openDirectory'] });
  return r.canceled || !r.filePaths.length ? null : r.filePaths[0];
});
ipcMain.handle('pick-files', async function() {
  const r = await dialog.showOpenDialog(win, {
    title: 'Elegí videos',
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'Videos', extensions: ['mkv', 'mp4', 'avi', 'mov', 'm4v', 'webm'] }, { name: 'Todos los archivos', extensions: ['*'] }]
  });
  return r.canceled ? [] : r.filePaths;
});
