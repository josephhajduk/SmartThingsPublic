/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Lifx Ternary Switch", namespace: "josephhajduk", author: "Joseph Hajduk") {
		capability "Polling"
		capability "Actuator"
		capability "Indicator"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"
		capability "Switch Level"
		capability "Color Control"
		capability "Color Temperature"

		command "setAdjustedColor"
		command "setColor"

		command "physicalOn"
		command "physicalOff"

		command "ternaryOn"
		command "ternaryOff"
		command "ternaryEither"

		attribute "physicalSwitch", "enum", ["on","off"]
		attribute "ternarySwitch", "enum", ["on", "off", "either"]
		attribute "colorName", "string"
		attribute "lifxGroupName", "string"
	}

	preferences {
		input "ledIndicator", "enum", title: "LED Indicator", description: "Turn LED indicator... ", required: false, options:["on": "When On", "off": "When Off", "never": "Never"], defaultValue: "off"
		input "lifxGroupId", "text", title: "Lifx Group Id", description: "Lifx Group Id", required: false
	}

	// tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.ternarySwitch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"ternaryOff", icon:"http://hosted.lifx.co/smartthings/v1/196xOn.png", backgroundColor:"#79b821"
				attributeState "off", label:'${name}', action:"ternaryOn", icon:"http://hosted.lifx.co/smartthings/v1/196xOff.png", backgroundColor:"#ffffff"
				attributeState "either", label:'${name}', action:"ternaryOff", icon:"st.Home.home30"
			}

			tileAttribute ("device.physicalSwitch", key: "SECONDARY_CONTROL") {
				attributeState "on", label:'${name}', action:"physicalOff", icon:"st.switches.switch.on", backgroundColor:"#79b821"
				attributeState "off", label:'${name}', action:"physicalOn", icon:"st.switches.switch.off", backgroundColor:"#ffffff"
			}

			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
			tileAttribute ("device.color", key: "COLOR_CONTROL") {
				attributeState "color", action:"setAdjustedColor"
			}
		}

		valueTile("colorName", "device.colorName", height: 2, width: 4, inactiveLabel: false, decoration: "flat") {
			state "colorName", label: '${currentValue}'
		}
		controlTile("colorTempSliderControl", "device.colorTemperature", "slider", height: 1, width: 6, inactiveLabel: false, range:"(2700..9000)") {
			state "colorTemp", action:"color temperature.setColorTemperature"
		}
		valueTile("colorTemp", "device.colorTemperature", inactiveLabel: false, decoration: "flat", height: 2, width: 2) {
			state "colorTemp", label: '${currentValue}K'
		}

		standardTile("indicator", "device.indicatorStatus", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "when off", action:"indicator.indicatorWhenOn", icon:"st.indicators.lit-when-off"
			state "when on", action:"indicator.indicatorNever", icon:"st.indicators.lit-when-on"
			state "never", action:"indicator.indicatorWhenOff", icon:"st.indicators.never-lit"
		}


		valueTile("groupName", "device.lifxGroupName", height: 2, width: 4, inactiveLabel: false, decoration: "flat") {
			state "lifxGroupName", label: '${currentValue}'
		}

		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "switch"
		details(["switch", "colorName","colorTempSliderControl", "colorTemp","indicator","groupName","refresh"])
	}
}

private getAccessToken() {
	return "c32f9b17fabf3bf9cc0d57faeafe7a8e51889047e1078ade4694868f7bbc9d8a";
}

def installed() {
	// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
}

def updated(){
	// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	switch (ledIndicator) {
		case "on":
			indicatorWhenOn()
			break
		case "off":
			indicatorWhenOff()
			break
		case "never":
			indicatorNever()
			break
		default:
			indicatorWhenOn()
			break
	}
	response(refresh())
}

def getCommandClassVersions() {
	[
			0x20: 1,  // Basic
			0x56: 1,  // Crc16Encap
			0x70: 1,  // Configuration
	]
}

private debug(debugData){
	log.debug(debugData)
}

private sendCommand(path, method="GET", body=null) {
	def accessToken = getAccessToken()
	def pollParams = [
			uri: "https://api.lifx.com",
			path: "/v1/"+path+".json",
			headers: ["Content-Type": "application/x-www-form-urlencoded", "Authorization": "Bearer ${accessToken}"],
			body: body
	]
	debug(method+" Http Params ("+pollParams+")")

	try{
		if(method=="GET"){
			httpGet(pollParams) { resp ->
				parseResponse(resp)
			}
		}else if(method=="PUT") {
			httpPut(pollParams) { resp ->
				parseResponse(resp)
			}
		}
	} catch(Exception e){
		debug("___exception: " + e)
	}
}


private parseResponse(resp) {
	debug("Response: "+resp.data)

	if (resp.status == 404) {
		sendEvent(name: "switch", value: "unreachable")
		debug("Unreachable")
		return []
	} else if (resp.data.results[0] != null){
		log.debug("Results: "+resp.data.results[0])
	}
	else {
		def power_data = resp.data.collect {
			nth -> return nth.power
		}
		def uniquePower = power_data.unique()

		def ref_light_data = resp.data[0]

		// if they are all on or all off use the first light
		if ((uniquePower != ["on"]) &&  (uniquePower != ["off"])) {
			// find the first on light
			ref_light_data = resp.data.find{ light -> light.power == "on"}
		}

		// we pick a ref light to set these values
		sendEvent(name: "level", value: Math.round((ref_light_data.brightness ?: 1) * 100))
		sendEvent(name: "color", value: colorUtil.hslToHex((ref_light_data.color.hue / 3.6) as int, (ref_light_data.color.saturation * 100) as int))
		sendEvent(name: "hue", value: ref_light_data.color.hue / 3.6)
		sendEvent(name: "saturation", value: ref_light_data.color.saturation * 100)
		sendEvent(name: "colorTemperature", value: ref_light_data.color.kelvin)
		sendEvent(name: "lifxGroupName", value: "${ref_light_data.group.name}")

		// if all the lights are on,  then set physical switch to on
		if (uniquePower == ["on"]) {
			return response(ternaryOn())
			// if all the lights are off,  then set physical switch to off
		} else if (uniquePower == ["off"]) {
			return response(ternaryOff())
			// otherwise
		} else {
			// set ternary switch to either
			ternaryEither()

			def onlights = power_data.count("on")
			def offlights = power_data.count("off")

			// if majority of lights are on,  then set physical switch to on
			if (onlights > offlights)
				return response(physicalOn())
			// otherwise set physical switch to off
			else
				return response(physicalOff())
		}

	}
	return []
}


private sendAdjustedColor(data, powerOn) {
	// def hue = data.hue*3.6
	// def saturation = data.saturation/100
	// def brightness = data.level/100

	// sendCommand("lights/"+device.deviceNetworkId+"/color", "PUT", 'color=hue%3A'+hue+'%20saturation%3A'+saturation+'%20brightness%3A'+brightness+'&duration=1&power_on='+powerOn)
	// sendCommand("lights/group_id:"+device.deviceNetworkId+"/state", "PUT", 'color=hue:'+hue+'%20saturation:'+saturation+'%20brightness:'+brightness+'&duration=1&power='+powerOn)
	// sendCommand("lights/group_id:"+device.deviceNetworkId+"/state", "PUT", [color: "brightness:${brightness}+saturation:${saturation}+hue:${hue}", "power": "on"])

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", [color: "saturation:${data.saturation / 100}+hue:${data.hue * 3.6}", "power": "on"])

	sendEvent(name: 'hue', value: data.hue)
	sendEvent(name: 'saturation', value: data.saturation)
	sendEvent(name: 'level', value: device.currentValue("level"))
	sendEvent(name: 'switch', value: "on")

}

def setAdjustedColor(value) {
	def data = [:]
	data.hue = value.hue
	data.saturation = value.saturation
	data.level = device.currentValue("level")

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", [color: "saturation:${data.saturation / 100}+hue:${data.hue * 3.6}", "power": "on"])

	//sendAdjustedColor(data, 'true')
	// sendEvent(name: "color", value: colorUtil.hslToHex((data.hue) as int, (data.saturation) as int))
	sendEvent(name: 'hue', value: value.hue)
	sendEvent(name: 'saturation', value: value.saturation)

	refresh()

}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		log.debug "Was hailed: requesting state update"
	} else {

		def pResult = result

		// if switch isn't in either mode, sync ternary with physical
		if (!state.either) {
			result = [pResult, createEvent([name: "ternarySwitch", value: pResult.value, type: pResult.type])]
		} else {
			result = [pResult, createEvent([name: "ternarySwitch", value: "either", type: "digital"])]
		}

		log.debug "Parse returned ${pResult?.descriptionText}"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	[name: "physicalSwitch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	[name: "physicalSwitch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	[name: "physicalSwitch", value: cmd.value ? "on" : "off", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	[name: "indicatorStatus", value: value, displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)
	updateDataValue("manufacturer", cmd.manufacturerName)
	createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def versions = commandClassVersions
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def physicalOn() {
	log.debug "physicalOn"

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", "power=on&duration=1")

	delayBetween([
			zwave.basicV1.basicSet(value: 0xFF).format(),
			zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def physicalOff() {
	log.debug "physicalOff"

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", "power=off&duration=1")

	delayBetween([
			zwave.basicV1.basicSet(value: 0x00).format(),
			zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def ternaryOn() {
	log.debug "ternaryOn"
	state.either = false
	sendEvent([name: "ternarySwitch", value: "on", type: "digital"])
	physicalOn()
}

def ternaryOff() {
	log.debug "ternaryOff"
	state.either = false
	sendEvent([name: "ternarySwitch", value: "off", type: "digital"])
	physicalOff()
}

def ternaryEither() {
	log.debug "ternaryEither"
	state.either = true
	sendEvent([name: "ternarySwitch", value: "either", type: "digital"])
}


def setLevel(double value) {
	def data = [:]
	data.hue = device.currentValue("hue")
	data.saturation = device.currentValue("saturation")
	data.level = value

	if (data.level < 1 && data.level > 0) {
		data.level = 1 // clamp to 1%
	}
	if (data.level == 0) {
		sendEvent(name: "level", value: 0) // Otherwise the level value tile does not update
		return off() // if the brightness is set to 0, just turn it off
	}

	def brightnes = data.level / 100

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", ["brightness": brightnes, "power": "on"])

	// sendAdjustedColor(data, 'true')
	sendEvent(name: 'level', value: value)
	sendEvent(name: 'switch', value: "on")
}

def setColor(value) {
	log.debug "setColor: ${value}"
	def data = [:]
	data.hue = value.hue
	data.saturation = value.saturation
	data.level = (value.level)?value.level:device.currentValue("level")
	// data.level = device.currentValue("level")

	sendAdjustedColor(data, 'true')
	sendEvent(name: 'hue', value: value.hue)
	sendEvent(name: 'saturation', value: value.saturation)
	sendEvent(name: 'switch', value: "on")
	sendEvent(name: 'level', value: value)
}


def setColorTemperature(value) {
	log.debug "Executing 'setColorTemperature' to ${value}"
	//parent.logErrors() {

	sendCommand("lights/group_id:"+lifxGroupId+"/state", "PUT", [color: "kelvin:${value}", power: "on"])
	//def resp = parent.apiPUT("/lights/${selector()}/state", [color: "kelvin:${kelvin}", power: "on"])
	//if (resp.status < 300) {
	def genericName = getGenericName(value)
	log.debug "generic name is : $genericName"

	sendEvent(name: "colorTemperature", value: value)
	sendEvent(name: "color", value: "#ffffff")
	sendEvent(name: "saturation", value: 0)
	sendEvent(name: "colorName", value: genericName)
	//} else {
	//	log.error("Bad setLevel result: [${resp.status}] ${resp.data}")
	//}

	//}
}

private getGenericName(value){
	def genericName = "Warm White"
	if(value < 2750){
		genericName = "Extra Warm White"
	} else if(value < 3300){
		genericName = "Warm White"
	} else if(value < 4150){
		genericName = "Moonlight"
	} else if(value < 5000){
		genericName = "Daylight"
	} else if(value < 6500){
		genericName = "Cool Light"
	} else if(value < 8000){
		genericName = "Extra Cool Light"
	} else if(value <= 9000){
		genericName = "Super Cool Light"
	}

	genericName
}


def poll() {
	refresh()
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 **/
def ping() {
	zwave.switchBinaryV1.switchBinaryGet().format()
}

def refresh() {
	log.debug "groupid: $lifxGroupId"

	delayBetween([
			zwave.switchBinaryV1.switchBinaryGet().format(),
			zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	])

	sendCommand("lights/group_id:"+lifxGroupId)
}

void indicatorWhenOn() {
	sendEvent(name: "indicatorStatus", value: "when on", displayed: false)
	sendHubCommand(new physicalgraph.device.HubAction(zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()))
}

void indicatorWhenOff() {
	sendEvent(name: "indicatorStatus", value: "when off", displayed: false)
	sendHubCommand(new physicalgraph.device.HubAction(zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()))
}

void indicatorNever() {
	sendEvent(name: "indicatorStatus", value: "never", displayed: false)
	sendHubCommand(new physicalgraph.device.HubAction(zwave.configurationV1.configurationSet(configurationValue: [2], parameterNumber: 3, size: 1).format()))
}

def invertSwitch(invert=true) {
	if (invert) {
		zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 4, size: 1).format()
	}
	else {
		zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 4, size: 1).format()
	}
}
