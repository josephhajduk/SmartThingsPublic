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
	definition (name: "Child Switch", namespace: "josephhajduk", author: "Joseph Hajduk", ocfDeviceType: "oic.d.switch", minHubCoreVersion: '000.017.0012', executeCommandsLocally: false) {
		capability "Actuator"
		capability "Indicator"
		capability "Switch"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"
		capability "Light"


		// faked on will stay on the switch until you turn it off
		// no on commands will be sent until the switch is first turned off
		// i.e. child switch will be faked on when parent is turned on.
		command "fakeOn"

		// faked off will stay off on the switch until you turn it on
		// no off commands will be sent until the switch is turned on
		// i.e. parent switch will be faked off when any child is turned off
		command "fakeOff"

		fingerprint mfr:"0063", prod:"4952", deviceJoinName: "GE Wall Switch"
		fingerprint mfr:"0063", prod:"5257", deviceJoinName: "GE Wall Switch"
		fingerprint mfr:"0063", prod:"5052", deviceJoinName: "GE Plug-In Switch"
		fingerprint mfr:"0113", prod:"5257", deviceJoinName: "Z-Wave Wall Switch"
	}

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	preferences {
		input "ledIndicator", "enum", title: "LED Indicator", description: "Turn LED indicator... ", required: false, options:["on": "When On", "off": "When Off", "never": "Never"], defaultValue: "off"
	}

	// tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			}
		}

		standardTile("indicator", "device.indicatorStatus", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "when off", action:"indicator.indicatorWhenOn", icon:"st.indicators.lit-when-off"
			state "when on", action:"indicator.indicatorNever", icon:"st.indicators.lit-when-on"
			state "never", action:"indicator.indicatorWhenOff", icon:"st.indicators.never-lit"
		}
		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "switch"
		details(["switch","refresh"])
	}
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
		log.debug "Parse returned ${result?.descriptionText}"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	if (state.fakeOn == true) {
		// only send offs
		if (cmd.value == "off")
			[name: "switch", value: "off", type: "physical"]
		else
			[:]
	} else if ( state.fakeOff) {
		// only send ons
		if (cmd.value == "on")
			[name: "switch", value: "off", type: "physical"]
		else
			[:]
	} else {
		// act normally
		[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	if (state.fakeOn == true) {
		// only send offs
		if (cmd.value == "off")
			[name: "switch", value: "off", type: "physical"]
		else
			[:]
	} else if ( state.fakeOff) {
		// only send ons
		if (cmd.value == "on")
			[name: "switch", value: "off", type: "physical"]
		else
			[:]
	} else {
		// act normally
		[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	if (state.fakeOn == true) {
		// only send offs
		if (cmd.value == "off")
			[name: "switch", value: "off", type: "digital"]
		else
			[:]
	} else if ( state.fakeOff) {
		// only send ons
		if (cmd.value == "on")
			[name: "switch", value: "off", type: "digital"]
		else
			[:]
	} else {
		// act normally
		[name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
	}
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

def on() {
	state.fakeOff = false
	delayBetween([
			zwave.basicV1.basicSet(value: 0xFF).format(),
			zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def fakeOn() {
	state.fakeOn = true
	on()
}

def off() {
	state.fakeOn = false
	delayBetween([
			zwave.basicV1.basicSet(value: 0x00).format(),
			zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def fakeOff() {
	state.fakeOff = true
	off()
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 **/
def ping() {
	zwave.switchBinaryV1.switchBinaryGet().format()
}

def refresh() {
	delayBetween([
			zwave.switchBinaryV1.switchBinaryGet().format(),
			zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	])
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
