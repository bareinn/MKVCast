// Puente seguro entre la interfaz (desktop.html) y las funciones nativas de la app de escritorio
const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  pickFolder: function() { return ipcRenderer.invoke('pick-folder'); },
  pickFiles: function() { return ipcRenderer.invoke('pick-files'); },
  // Ruta real en disco de un archivo arrastrado a la ventana
  pathFor: function(file) {
    try { return webUtils.getPathForFile(file); } catch (e) { return file && file.path ? file.path : ''; }
  }
});
