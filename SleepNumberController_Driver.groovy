/**
 *  Sleep Number Controller Driver
 *
 *  Usage:
 *    Allows controlling Sleep Number Flexible bases including presence detection.
 *    This driver uses the mode and side to communicate back to the parent app where all controls happen.
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you!
 * 
 *  Thanks to Nathan Jacobson and Tim Parsons for their work on SmartThings apps that do this.  This isn't a copy
 *  of those but leverages prior work they"ve done for the API calls and bed management.
 *    https://github.com/natecj/SmartThings/blob/master/smartapps/natecj/sleepiq-manager.src/sleepiq-manager.groovy
 *    https://github.com/ClassicTim1/SleepNumberManager/blob/master/FlexBase/SmartApp.groovy
 */
import groovy.transform.Field

@Field final ArrayList TYPES = ["presence", "head", "foot", "foot warmer"]
@Field final ArrayList SIDES = ["Right", "Left"]
@Field final Map HEAT_TEMPS = [Off: 0, Low: 31, Medium: 57, High: 72]
@Field final Map HEAT_TIMES = ["30m": 30, "1h": 60, "2h": 120, "3h": 180, "4h": 240, "5h": 300, "6h": 360]
@Field final Map ACTUATOR_TYPES = [head: "H", foot: "F"]
@Field final Map PRESET_TIMES = ["Off": 0, "15m": 15, "30m": 30, "45m": 45, "1h": 60, "2h": 120, "3h": 180]
@Field final Map PRESET_NAMES = [Favorite: 1, Flat: 4, ZeroG: 5, Snore: 6, WatchTV: 3, Read: 2]
@Field final ArrayList UNDERBED_LIGHT_STATES = ['Auto', 'On', 'Off']
@Field final Map UNDERBED_LIGHT_BRIGHTNESS = [Low: 1, Medium: 30, High: 100]
@Field final Map UNDERBED_LIGHT_TIMES = ["Forever": 0, "15m": 15, "30m": 30, "45m": 45, "1h": 60, "2h": 120, "3h": 180]

metadata {
  definition(name: "Sleep Number Bed",
             namespace: "rvrolyk",
             author: "Russ Vrolyk",
             importUrl: "https://raw.githubusercontent.com/rvrolyk/SleepNumberController/master/SleepNumberController_Driver.groovy"
  ) {
    capability "Actuator"
    capability "Switch"
    capability "SwitchLevel"
    capability "PresenceSensor"
    capability "Polling"

    attribute "headPosition", "number"
    attribute "footPosition", "number"
    attribute "footWarmingTemp", "enum", HEAT_TEMPS.collect{ it.key }
    attribute "footWarmingTimer", "enum", HEAT_TIMES.collect{ it.key }
    attribute "sleepNumber", "number"
    // The user's sleep number favorite
    attribute "sleepNumberFavorite", "number"
    // The current preset position of the side
    attribute "positionPreset", "string"
    // The preset that the bed will change to once timer is done
    attribute "positionPresetTimer", "string"
    // The timer for the preset change
    attribute "positionTimer", "number"
    attribute "privacyMode", "enum", ["on", "off"]
    // Attributes for sleep IQ data
    attribute "sleepMessage", "string"
    attribute "sleepScore", "number"
    attribute "restfulAverage", "string"
    attribute "restlessAverage", "string"
    attribute "heartRateAverage", "number"
    attribute "HRVAverage", "number"
    attribute "breathRateAverage", "number"
    attribute "outOfBedTime", "string"
    attribute "inBedTime", "string"
    attribute "timeToSleep", "string"
    attribute "sessionStart", "date"
    attribute "sessionEnd", "date"
    attribute "sleepDataRefreshTime", "date"
    attribute "sleepIQSummary", "string"
    attribute "sessionSummary", "string"

    command "setRefreshInterval", [[name: "interval", type: "NUMBER", constraints: ["NUMBER"]]]
    command "arrived"
    command "departed"
    command "setSleepNumber", [[name: "sleep number", type: "NUMBER", constraints: ["NUMBER"]]]
    command "setBedPosition", [[name: "position", type: "NUMBER", constraints: ["NUMBER"]],
      [name: "actuator", type: "ENUM", constraints: ACTUATOR_TYPES.collect{ it.value }]]
    command "setFootWarmingState", [[name: "temp", type: "ENUM", constraints: HEAT_TEMPS.collect{ it.key }],
       [name: "timer", type: "ENUM", constraints: HEAT_TIMES.collect{ it.key }]]
    command "setBedPreset", [[name: "preset", type: "ENUM", constraints: PRESET_NAMES.collect{ it.key }]]
    command "setBedPresetTimer", [[name: "preset", type: "ENUM", constraints: PRESET_NAMES.collect{ it.key }],
        [name: "timer", type: "ENUM", constraints: PRESET_TIMES.collect{ it.key }]]
    command "stopBedPosition"
    command "enablePrivacyMode"
    command "disablePrivacyMode"
    command "getSleepData"
    command "setSleepNumberFavorite"
    command "setUnderbedLightState", [[name: "state", type: "ENUM", constraints: UNDERBED_LIGHT_STATES],
        [name: "timer", type: "ENUM", constraints: UNDERBED_LIGHT_TIMES.collect{ it.key }],
        [name: "brightness", type: "ENUM", constraints: UNDERBED_LIGHT_BRIGHTNESS.collect{ it.key }]]
  }

  preferences {
    section("Settings:") {
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "presetLevel", type: "enum", title: "Preset level for 'on'", description: "Only valid for device type that is not 'foot warmer'", options: PRESET_NAMES.collect{ it.key }, defaultValue: "Favorite"
      input name: "footWarmerLevel", type: "enum", title: "Warmer level for 'on'", description: "Only valid for device type that is 'foot warmer'", options: HEAT_TEMPS.collect{ it.key }, defaultValue: "Medium"
      input name: "footWarmerTimer", type: "enum", title: "Warmer duration for 'on'", description: "Only valid for device type that is 'foot warmer'", options: HEAT_TIMES.collect{ it.key }, defaultValue: "30m"
      input name: "enableSleepData", type: "bool", title: "Enable sleep data collection", defaultValue: false
      input name: "enableChildDevices", type: "bool", title: "Enable child devices for switches (Presence device only)", defaultValue: false
    }
  }
}

def installed() {
  debug "installed()"
  updated()
}

def logsOff() {
  log.warn "debug logging disabled..."
  device.updateSetting "logEnable", [value: "false", type: "bool"]
}

def updated() {
  debug "updated()"
   if (enableChildDevices) {
      manageChildren()      
  }
  if (logEnable) {
    runIn(1800, logsOff)
  }
  if (enableSleepData) {
    if (state.type == "presence") {
      getSleepData()
    } else {
      // only valid for presence so turn it back off
      log.info "Sleep data only available on presence device, this is ${state.type}"
      device.updateSetting "enableSleepData",  [value: false, type: "bool"]
    }
  }
  // Poll right after updated / installed
  poll()
}

def uninstalled() {
   deleteChildDevice(getChildDNI("outlet"))
   deleteChildDevice(getChildDNI("underbedlight"))
}

def manageChildren() {
    switch (state.type) {
        case "presence":
            def outlet = getOutlet()
            if (!outlet) {
                outlet = addChildDevice('hubitat','Generic Component Switch', getChildDNI("outlet"),
                                        [ label: getChildName("Outlet"),
                                         componentName: "outlet",
                                         componentLabel: getChildName("Outlet"),                	                  
                                         completedSetup:true])
                log.info("Created Outlet child device")
            }
            def underbed = getUnderbed()
            if (!underbed) {
                outlet = addChildDevice('hubitat','Generic Component Switch', getChildDNI("underbedlight"),
                                        [ label: getChildName("Underbed Light"),
                                         componentName: "underbedlight",
                                         componentLabel: getChildName("Underbed Light"),                	                  
                                         completedSetup:true])
                log.info("Created Underbed light child device")
            }
            break
    }
}

def getOutlet() {
    return childDevices.find({it.deviceNetworkId == getChildDNI("outlet")})
}

def getUnderbed() {
    return childDevices.find({it.deviceNetworkId == getChildDNI("underbedlight")})
}


def getChildName(name) {
    return "Sleep Number ${state.side} - ${name}"
}

def getChildDNI(name) {
	return device.deviceNetworkId + "-" + name
}

def getChildDNI(type, name) {
    return device.deviceNetworkId + + "-${type}-${name}"
}

def componentRefresh(device) {
    log.warn("Component Refresh not implemented")
}

def componentOn(device) {    
    def o = getOutlet()  
    def u = getUnderbed()  
    if (device.deviceNetworkId == o?.deviceNetworkId) {     
        setOutletState('On')
        device.sendEvent([[name:"switch", value: "on"]])
    }
    else if (device.deviceNetworkId == u?.deviceNetworkId) {
        setUnderbedLightState('On')
        device.sendEvent([[name:"switch", value: "on"]])
    }
}

def componentOff(device) {
    def o = getOutlet()   
     def u = getUnderbed()  
    if (device.deviceNetworkId == o?.deviceNetworkId) {        
        setOutletState('Off')
        device.sendEvent([[name:"switch", value: "off"]])
    }
    else if (device.deviceNetworkId == u?.deviceNetworkId) {
        setUnderbedLightState('Off')
        device.sendEvent([[name:"switch", value: "off"]])
    }
}


def parse(String description) {
  debug "parse() - Description: ${description}"
}

// Required by Polling capability
def poll() {
  debug "poll()"
  sendToParent "refreshChildDevices"
}

// Required by Switch capability
def on() {
  sendEvent name: "switch", value: "on"
  if (state.type == "foot warmer") {
    debug "on(): foot warmer"
    setFootWarmingState(footWarmerLevel, footWarmerTimer)
  } else {
    debug "on(): set preset ${presetLevel}"
    setBedPreset(presetLevel)
  }
}

// Required by Switch capability
def off() {
  sendEvent name: "switch", value: "off"
  if (state.type == "foot warmer") {
    debug "off(): foot warmer"
    setFootWarmingState("Off")
  } else {
    debug "off(): set Flat"
    setBedPreset("Flat")
  }
}

// setLevel required by SwitchLevel capability
// including one with duration (which we ignore).
def setLevel(val, duration) {
  setLevel(val)
}

def setLevel(val) {
  switch (state.type) {
    case "presence":
      debug "setLevel(${val}): sleepNumber"
      setSleepNumber(val)
      break
    case "head":
      debug "setLevel(${val}): head position"
      setBedPosition(val)
      break
    case "foot":
      debug "setLevel(${val}): foot position"
      setBedPosition(val)
      break
    case "foot warmer":
      def level = 0
      switch (val) {
        case 1:
          level = "Low"
          break
        case 2:
          level = "Medium"
          break
        case 3:
          level = "High"
          break
      }
      if (!level) {
        log.error "Invalid level for warmer state.  Only 1, 2 or 3 is valid"
        return
      }
      debug "setLevel(${val}): warmer level to ${level}"
      setFootWarmingState(level)
      break;
  }
  sendEvent name: "level", value: val
}

// Required by PresenceSensor capability
def isPresent() {
  return device.currentValue("presence") == "present"
}

def arrived() {
  debug "arrived()"
  if (!isPresent() && state.type == "presence") {
    log.info "${device.displayName} arrived"
    sendEvent name: "presence", value: "present"
  }
}

def departed() {
  debug "departed()"
  if (isPresent() && state.type == "presence") {
    log.info "${device.displayName} departed"
    sendEvent name: "presence", value: "not present"
    if (enableSleepData) {
      getSleepData()
    }
  }
}

def setPresence(val) {
  debug "setPresence(${val})"
  if (val) {
    arrived()
  } else {
    departed()
  }
}

def setBedId(val) {
  debug "setBedId(${va}l)"
  state.bedId = val
}

def setType(val) {
  debug "setType(${val})"
  if (!TYPES.contains(val)) {
    log.error "Invalid type ${val}, possible values are ${TYPES}"
  }
  def msg = "${val.capitalize()} - "
  switch (val) {
    case "presence":
      msg += "on/off will switch between preset level (on) and flat (off).  Dimming changes the Sleep Number."
      break
    case "head":
      msg += "on/off will switch between preset level (on) and flat (off).  Dimming changes the head position (0 is flat, 100 is fully raised)."
      break
    case "foot":
      msg += "on/off will switch between preset level (on) and flat (off).  Dimming changes the foot position (0 is flat, 100 is fully raised)."
      break
    case "foot warmer":
      msg += "off switches foot warming off, on will set it to preferred value for preferred time. Dimming changes the heat levels (1: low, 2: medium, 3: high)."
      break
  } 
  state.typeInfo = msg
  state.type = val
}

def setSide(val) {
  debug "setSide(${val})"
  if (!SIDES.contains(val)) {
    log.error "Invalid side ${val}, possible values are ${SIDES}"
  }
  state.side = val
}

def setRefreshInterval(val) {
  debug "setRefreshInterval(${val})"
  sendToParent "setRefreshInterval", val
}

def setSleepNumber(val) {
  debug "setSleepNumber(${val})"
  if (val > 0 && val <= 100) {
    sendToParent "setSleepNumber", val
  } else {
    log.error "Invalid number, must be between 1 and 100"
  }
}

def setBedPosition(val, actuator = null) {
  debug "setBedPosition(${val})"
  def type = actuator ?: ACTUATOR_TYPES.get(state.type)
  if (!type) {
    log.error "Cannot determine actuator"
    return
  }
  if (val >= 0 && val <= 100) {
    sendToParent "setFoundationAdjustment", [actuator: type, position: val]
  } else {
    log.error "Invalid position, must be between 0 and 100"
  }
}

def setFootWarmingState(temp = "OFF", timer = "30m") {
  debug "setWarmerState(${temp}, ${duration})"
  if (HEAT_TEMPS.get(temp) == null) {
    log.error "Invalid warming temp ${temp}"
    return
  }
  if (!HEAT_TIMES.get(timer)) {
    log.error "Invalid warming time ${timer}"
    return
  }
  sendToParent "setFootWarmingState", [temp: HEAT_TEMPS.get(temp), timer: HEAT_TIMES.get(timer)]
}

def setBedPreset(preset) {
  debug "setBedPreset(${preset})"
  if (preset == null || PRESET_NAMES.get(preset) == null) {
    log.error "Invalid preset ${preset}"
    return
  }
  sendToParent "setFoundationPreset", PRESET_NAMES.get(preset)
}

def setBedPresetTimer(preset, timer) {
  debug "setBedPresetTimer(${preset}, ${timer})"
  if (preset == null || PRESET_NAMES.get(preset) == null) {
    log.error "Invalid preset ${preset}"
    return
  }
  if (timer == null || PRESET_TIMES.get(timer) == null) {
    log.error "Invalid preset timer ${timer}"
    return
  }
  sendToParent "setFoundationTimer", [preset: PRESET_NAMES.get(preset), timer: PRESET_TIMES.get(timer)]
}

def stopBedPosition() {
  debug "stopBedPostion()"
  sendToParent "stopFoundationMovement"
}

def enablePrivacyMode() {
  debug "enablePrivacyMode()"
  sendToParent "setPrivacyMode", true
}

def disablePrivacyMode() {
  debug "disablePrivacyMode()"
  sendToParent "setPrivacyMode", false
}

/**
 * Sets the SleepNumber to the preset favorite.
 */
def setSleepNumberFavorite() {
  debug "setSleepNumberFavorite()"
  sendToParent "setSleepNumberFavorite"
}

def setUnderbedLightState(state, timer = "Forever", brightness = "High") {
  debug "setUnderbedLightState(${state}, ${timer}, ${brightness})"
  if (state == null || !UNDERBED_LIGHT_STATES.contains(state)) {
    log.error "Invalid state ${state}"
    return
  }
  if (timer != null && UNDERBED_LIGHT_TIMES.get(timer) == null) {
    log.error "Invalid timer ${timer}"
    return
  }
  if (brightness && !UNDERBED_LIGHT_BRIGHTNESS.get(brightness)) {
    log.error "Invalid brightness ${brightness}"
    return
  }
  sendToParent "setUnderbedLightState", [state: state,
    timer: UNDERBED_LIGHT_TIMES.get(timer),
    brightness: UNDERBED_LIGHT_BRIGHTNESS.get(brightness)]
}

def getSleepData() {
  if (state.type != "presence") {
    log.error "Sleep data only available on presence (main) device, this is ${state.type}"
    return
  }
  def data = sendToParent "getSleepData"
  debug "sleep data ${data}"

  if (data.sleepSessionCount == 0) {
    log.info "No sleep sessions found, skipping update"
    return
  }

  // Set basic attributes
  // device.currentValue(name, true) doesn't seem to avoid the cache so stash the values
  // used in the summary tiles.
  sendEvent name: "sleepDataRefreshTime", value: new Date(now()).format("yyyy-MM-dd'T'HH:mm:ssXXX")
  sendEvent name: "sleepMessage", value: data.sleepData.message.find{it != ''}
  def sleepScore = data.sleepIQAvg
  sendEvent name: "sleepScore", value: sleepScore
  def restfulAvg = convertSecondsToTimeString(data.restfulAvg)
  sendEvent name: "restfulAverage", value: restfulAvg
  def restlessAvg = convertSecondsToTimeString(data.restlessAvg)
  sendEvent name: "restlessAverage", value: restlessAvg
  def heartRateAvg = data.heartRateAvg
  sendEvent name: "heartRateAverage", value: heartRateAvg
  def hrvAvg = data.hrvAvg
  sendEvent name: "HRVAverage", value: hrvAvg
  def breathRateAvg = data.respirationRateAvg
  sendEvent name: "breathRateAverage", value: breathRateAvg
  def outOfBedTime = convertSecondsToTimeString(data.outOfBedTotal)
  sendEvent name: "outOfBedTime", value: outOfBedTime
  def inBedTime = convertSecondsToTimeString(data.inBedTotal)
  sendEvent name: "inBedTime", value: inBedTime
  def timeToSleep = convertSecondsToTimeString(data.fallAsleepPeriod)
  sendEvent name: "timeToSleep", value: timeToSleep
  sendEvent name: "sessionStart", value: data.sleepData.sessions[0].startDate[0]
  sendEvent name: "sessionEnd", value: data.sleepData.sessions[data.sleepData.sessions.size() - 1].endDate[0]

  String table = '<table class="sleep-tiles %extraClasses" style="width:100%;font-size:12px;font-size:1.5vmax" id="%id">'
  // Set up tile attributes
  // Basic tile to represent what app shows when launched: last score, heart rate, hrv, breath rate
  String iqTile = table.replaceFirst('%id', "sleepiq-summary-${device.getLabel().toLowerCase().replaceAll(" ", "_")}")
      .replaceFirst('%extraClasses', "sleepiq-summary")
  iqTile += '<tr><th style="text-align: center; width: 50%">SleepIQ Score</th><th style="text-align: center">Breath Rate</th></tr>'
  iqTile += '<tr><td style="text-align: center">'
  iqTile += "${sleepScore}</td>"
  iqTile += '<td style="text-align: center">' + breathRateAvg + '</td></tr>'
  iqTile += '<tr><th style="text-align: center">Heart Rate</th><th style="text-align: center">HRV</th></tr>'
  iqTile += '<tr><td style="text-align: center">'
  iqTile += "${heartRateAvg}</td>"
  iqTile += '<td style="text-align: center">' + hrvAvg + '</td></tr>'
  iqTile += '</table>'
  sendEvent name: "sleepIQSummary", value: iqTile
  // Basic tile to aggregate session stats: time in bed, time to sleep, restful, restless, bed exits
  String summaryTile = table.replaceFirst('%id', "session-summary-${device.getLabel().toLowerCase().replaceAll(" ", "_")}")
      .replaceFirst('%extraClasses', "session-summary")
  summaryTile += "<tr><td colspan=2>In bed for ${inBedTime}</td></tr>"
  summaryTile += '<tr><th style="text-align: center; width: 50%">Time to fall asleep</th><th style="text-align: center">Restful</th></tr>'
  summaryTile += '<tr><td style="text-align: center">' + timeToSleep + '</td>'
  summaryTile += '<td style="text-align: center">' + restfulAvg + '</td></tr>'
  summaryTile += '<tr><th style="text-align: center">Restless</th><th style="text-align: center">Bed Exit</th></tr>'
  summaryTile += '<tr><td style="text-align: center">' + restlessAvg + '</td>'
  summaryTile += '<td style="text-align: center">' + outOfBedTime + '</td>'
  summaryTile += '</tr></table>'
  sendEvent name: "sessionSummary", value: summaryTile
}

def convertSecondsToTimeString(int secondsToConvert) {
  new GregorianCalendar(0, 0, 0, 0, 0, secondsToConvert, 0).time.format("HH:mm:ss")
}

// Method used by parent app to set bed state
def setStatus(Map params) {
  debug "setStatus(${params})"
  def validAttributes = device.supportedAttributes.collect{ it.name }
  params.each{param ->
    if (param.key in validAttributes) {
      def attributeValue = device."current${param.key.capitalize()}"
      def value = param.value
      // Translate heat temp to something more meaningful but leave the other values alone
      if (param.key == "footWarmingTemp") {
        value = HEAT_TEMPS.find{ it.value == value }
        if (value == null) {
          log.error "Invalid foot warming temp ${param.value}"
        } else {
          value = value.key
        }
      }
      if (attributeValue.toString() != value.toString()) {
        debug "Setting ${param.key} to ${value}, it was ${attributeValue}"
        // If this is a head or foot device, we need to sync level with the relevant
        // position, if it's presence, then we sync level with the sleep number value.
        if ((state.type == "head" && param.key == "headPosition")
            || (state.type == "foot" && param.key == "footPosition")
            || (state.type == "presence" && param.key == "sleepNumber")) {
          sendEvent name: "level", value: value
        }
        if (state.type != "foot warmer" && param.key == "positionPreset") {
          if (value == "Flat") {
            sendEvent name: "switch", value: "off"
          } else if (value == presetLevel) {
            // On if the level is the desired preset.
            // Note this means it's off even when raised if it doesn't match a preset which
            // may not make sense given there is a level.  But since it can be "turned on"
            // when not at preset level, the behavior (if not the indicator) seems logical.
            sendEvent name: "switch", value: "on"
          }
        }
        if (state.type == "foot warmer" && param.key == "footWarmingTemp") {
          def level = 0
          switch (value) {
              case "Off":
                level = 0
                break
              case "Low":
                level = 1
                break
              case "Medium":
                level = 2
                break
              case "High":
                level = 3
                break
          }
          sendEvent name: "level", value: level
          sendEvent name: "switch", value: level > 0 ? "on" : "off"
        }
        sendEvent name: param.key, value: value
      }
    } else {
      log.error "Invalid attribute ${param.key}"
    }
  }
}

def sendToParent(method, data = null) {
  debug "sending to parent ${method}, ${data}"
  if (device.parentDeviceId) {
    // Send via virtual container method
    parent.childComm method, data, device.deviceNetworkId
  } else {
    parent."${method}" data, device.deviceNetworkId
  }
}

def debug(msg) {
  if (logEnable) {
    log.debug msg
  }
}

// vim: tabstop=2 shiftwidth=2 expandtab

