'use strict'
// Test-only offline protocol client. Server endpoint is deliberately loopback-only.
const mc = require('minecraft-protocol')
const readline = require('node:readline')
const port = Number(process.argv[2] || 25568)
const username = process.argv[3] || 'AetherHomeTest'
if (!Number.isInteger(port) || port < 1024 || port > 65535 || !/^[A-Za-z0-9_]{1,16}$/.test(username)) throw Error('Invalid test endpoint or username')
const emit = (event, fields = {}) => process.stdout.write(JSON.stringify({ event, at: Date.now(), ...fields }, (_, v) => typeof v === 'bigint' ? v.toString() : v) + '\n')
const client = mc.createClient({ host: '127.0.0.1', port, username, auth: 'offline', version: '26.1', keepAlive: true, disableChatSigning: true, clientSettings: { viewDistance: 2 } })
let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }, ready = false, positioned = false
client.on('playerJoin', () => { ready = true; emit('ready', { username, uuid: client.uuid, clientVersion: '26.1', protocol: 775 }) })
client.on('position', packet => {
  for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) position[key] = (packet.flags?.[key] ? position[key] : 0) + packet[key]
  client.write('teleport_confirm', { teleportId: packet.teleportId })
  client.write('position_look', { ...position, flags: { onGround: true, hasHorizontalCollision: false } })
  if (!positioned) client.write('player_loaded', {})
  positioned = true
  emit('position', { ...position, teleportId: packet.teleportId })
})
client.on('ping', packet => client.write('pong', { id: packet.id }))
client.on('chunk_batch_finished', () => client.write('chunk_batch_received', { chunksPerTick: 10 }))
for (const name of ['systemChat', 'playerChat']) client.on(name, packet => emit('message', { kind: name, message: packet }))
for (const name of ['action_bar', 'set_title_text', 'boss_bar', 'scoreboard_objective', 'sound_effect']) {
  client.on(name, packet => emit('display', { kind: name, packet }))
}
let openWindow = null
client.on('open_window', packet => { openWindow = { id: packet.windowId, stateId: 0 }; emit('menu_open', packet) })
client.on('window_items', packet => { if (openWindow?.id === packet.windowId) openWindow.stateId = packet.stateId })
client.on('set_slot', packet => { if (openWindow?.id === packet.windowId) openWindow.stateId = packet.stateId })
client.on('close_window', packet => { openWindow = null; emit('menu_close', packet) })
client.on('game_state_change', packet => emit('weather', packet))
client.on('kick_disconnect', packet => emit('kick', { packet }))
client.on('disconnect', packet => emit('kick', { packet }))
client.on('error', error => emit('error', { message: error.message }))
client.on('end', reason => { ready = false; clearInterval(tick); emit('end', { reason }); process.exit(0) })
const tick = setInterval(() => {
  if (!ready || !positioned || client.state !== 'play') return
  client.write('flying', { flags: { onGround: true, hasHorizontalCollision: false } })
  client.write('tick_end', {})
}, 50)
let sequence = 0
readline.createInterface({ input: process.stdin }).on('line', line => {
  try {
    const request = JSON.parse(line)
    if (request.op === 'quit') { client.end('test complete'); return }
    if (request.op === 'position') { emit('position_snapshot', { id: request.id, ...position }); return }
    if (request.op === 'menu_click' && openWindow) {
      client.write('window_click', { windowId: openWindow.id, stateId: openWindow.stateId,
        slot: request.slot, mouseButton: request.button, mode: request.mode, changedSlots: [], cursorItem: null })
      return
    }
    if (request.op === 'menu_close' && openWindow) {
      client.write('close_window', { windowId: openWindow.id })
      openWindow = null
      return
    }
    if (ready && (request.op === 'break' || request.op === 'place')) {
      const { x, y, z } = request
      if (![x, y, z].every(Number.isSafeInteger)) throw Error('Expected integer block coordinates')
      const location = { x, y, z }
      if (request.op === 'break') {
        client.write('block_dig', { status: 0, location, face: 1, sequence: ++sequence })
      } else {
        client.write('block_place', { hand: 'main_hand', location, direction: 1, cursorX: 0.5,
          cursorY: 1, cursorZ: 0.5, insideBlock: false, worldBorderHit: false, sequence: ++sequence })
      }
      return
    }
    if (request.op !== 'command' || !ready || typeof request.command !== 'string' || request.command.length > 256 || /[\r\n]/.test(request.command)) throw Error('Invalid command request or client not ready')
    client.chat('/' + request.command.replace(/^\//, ''))
    emit('command_sent', { id: request.id, command: request.command })
  } catch (error) { emit('request_error', { message: error.message }) }
})
