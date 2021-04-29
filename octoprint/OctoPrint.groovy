metadata {
    definition (name: "OctoPrint", namespace: "OctoPrint", author: "MC", importUrl: "https://raw.githubusercontent.com/mikec85/hubitatdrivers/master/octoprint/OctoPrint.groovy") {
        capability "Actuator"
		capability "Initialize"
        //capability "Switch"
        capability "PresenceSensor"
        
        attribute "state", "enum", ["Operational", "Printing", "Pausing", "Paused", "Cancelling", "Error", "Offline", "Disconnected"]
        attribute "stateMessage", "string"
        attribute "completion", "string"
        attribute "printTimeLeft", "string"
        attribute "printTime", "string"
        attribute "estimatedPrintTime", "string"
        attribute "name", "string"
        attribute "user", "string"
		attribute "lastPrinterCheck", "string"
        
		
		// attributes for temperature
		attribute "bed-actual", "number"
		attribute "bed-offset", "number"
		attribute "bed-target", "number"
		// primary extruder
		attribute "tool0-actual", "number"
		attribute "tool0-offset", "number"
		attribute "tool0-target", "number"
		// additional extruders (if available on printer)
		attribute "tool1-actual", "number"
		attribute "tool1-offset", "number"
		attribute "tool1-target", "number"
		attribute "tool2-actual", "number"
		attribute "tool2-offset", "number"
		attribute "tool2-target", "number"


		// attributes for DisplayLayerProgress addon (if installed and enabled in hubitat device)
		attribute "DLP_fanSpeed", "string"
		attribute "DLP_feedrate", "number"
		attribute "DLP_feedrateG0", "number"
		attribute "DLP_feedrateG1", "number"
		attribute "DLP_layer_averageLayerDuration", "string"
		attribute "DLP_layer_averageLayerDurationInSeconds", "number"
		attribute "DLP_layer_current", "number"
		attribute "DLP_layer_lastLayerDuration", "string"
		attribute "DLP_layer_lastLayerDurationInSeconds", "number"
		attribute "DLP_layer_total", "number"
		attribute "DLP_print_changeFilamentCount", "number"
		attribute "DLP_print_changeFilamentTimeLeft", "string"
		attribute "DLP_print_changeFilamentTimeLeftInSeconds", "number"
		attribute "DLP_print_estimatedChangedFilamentTime", "string"
		attribute "DLP_print_estimatedEndTime", "string"
		attribute "DLP_print_m73progress", "number"
		attribute "DLP_print_printerState", "string"
		attribute "DLP_print_progress", "number"
		attribute "DLP_print_timeLeft", "string"
		attribute "DLP_print_timeLeftInSeconds", "number"
		attribute "DLP_height_current", "number"
		attribute "DLP_height_currentFormatted", "number"
		attribute "DLP_height_total", "number"
		attribute "DLP_height_totalFormatted", "number"


        command "CheckPrinter", null
        command "Print", null
        command "Restart", null
        command "Cancel", null
        command "Pause", null
        command "Resume", null
        command "PauseToggle", null
        command "ConnectToPrinter", null
    }

    preferences {
        section("Device Settings:") {
            input "ip_addr", "string", title:"ip address", description: "", required: true, displayDuringSetup: true
            input "url_port", "string", title:"tcp port", description: "", required: true, displayDuringSetup: true, defaultValue: "80"
            input "api_key", "string", title:"API Key", description: "", required: true, displayDuringSetup: true, defaultValue: ""
            input "enableDisplayLayerProgress", "bool", title:"Get Layer Progress", description: "Gets Layer Progress.  **Requires the octoprint addon: DisplayLayerProgress**", required: true, displayDuringSetup: true, defaultValue: false
            
            input "delayCheckIdle", "number", title:"Number of seconds between checking printer while idle", description: "", required: true, displayDuringSetup: true, defaultValue: "600"
            input "delayCheckPrinting", "number", title:"Number of seconds between checking printer while printing", description: "After a print, this short delay will be used to refresh the printer data until the Primary Extruder (tool0) is less than 50C", required: true, displayDuringSetup: true, defaultValue: "60"
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
            input name: "autoUpdate", type: "bool", title: "Enable Auto Updating of Printer Status", defaultValue: true
        }
    }


}        
void parse(String toparse){
    if (logEnable) log.info "Parsing: ${toparse}"
}

void initialize(){
	state.isPrinting = false
	unschedule(CheckPrinter)
    if (autoUpdate) runIn(1, CheckPrinter)
}

void uninstalled(){
	unschedule()
}

def updated(){
	if (logEnable) {
		log.warn "debug logging enabled..."
		runIn(1800,logsOff)
	}
	state.isPrinting = false
	unschedule(CheckPrinter)
    if (autoUpdate) runIn(5, CheckPrinter)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def CheckPrinter() {
	unschedule(CheckPrinter)
	GetPrinter()
}

def GetPrinter() {
	
	def nowDay = new Date().format("MMM dd", location.timeZone)
	def nowTime = new Date().format("h:mm:ss a", location.timeZone)
	sendEvent(name: "lastPrinterCheck", value: nowDay + " at " + nowTime, displayed: false)	
    
    def wxURI2 = "http://${ip_addr}:${url_port}/api/job"
    def toReturn = " "
        
    def requestParams2 =
	[
		uri:  wxURI2,
        headers: [ 
                   "User-Agent": "Wget/1.20.1",
                   Accept: "*/*",
                   "Accept-Encoding": "identity",
                   Host: "${ip_addr}",
                   Connection: "Keep-Alive",
                   "X-Api-Key": "${api_key}",
                 ],
	]
    try{
		httpGet(requestParams2)
		{
		  response ->
			if (response?.status == 200)
			{
				def stateName,msg
				if(response.data.state){
					if(response.data.state.contains(" (")){
						def rSplit = response.data.state.split(' \\(', 2)
						stateName = rSplit[0]
						msg = rSplit[1]
					} else {
						stateName = response.data.state
					}
				}
				if(state.state == null || state.state != stateName){
					sendEvent(name: "state", value: stateName)
					state.state = stateName
				}
				if (msg != "" && msg != null && msg.charAt(msg.length() - 1) == ')') {
					msg = msg.substring(0, msg.length() - 1);
				} else {
					msg = "none"
				}
				if(device.currentValue("stateMessage") != msg){
					sendEvent(name: "stateMessage", value: msg)
				}
				if(state.state != null && ["Printing", "Pausing", "Paused", "Cancelling"].contains(state.state)){
					state.isPrinting = true
				} else {
					state.isPrinting = false
				}
				
				if(state.state != null && (state.state.contains("Disconnected") || state.state.contains("Offline"))){
					state.printerConnected = false
					sendEvent(name: "presence", value: "not present")
				} else {
					state.printerConnected = true
					sendEvent(name: "presence", value: "present")
				}				
				
				if (state.isPrinting && response.data.progress.completion != null)
					{
						//state.completion = response.data.progress.completion
						def completionValue = Math.round(response.data.progress.completion.toDouble().trunc())
						sendEvent(name: "completion", value: completionValue)
					} else {
						sendEvent(name: "completion", value: 0 )
					}
				if (state.isPrinting && response.data.progress.printTimeLeft != null)
					{
						//state.printTimeLeft = response.data.progress.printTimeLeft/60
						sendEvent(name: "printTimeLeft", value: response.data.progress.printTimeLeft/60 )
					} else {
						sendEvent(name: "printTimeLeft", value: 0 )
					}
				if (state.isPrinting && response.data.progress.printTime != null)
					{
						//state.printTime = response.data.progress.printTime/60
						sendEvent(name: "printTime", value: response.data.progress.printTime/60 )
					} else {
						sendEvent(name: "printTime", value: 0 )
					}
				
				if (state.isPrinting && response.data.job.estimatedPrintTime != null)
					{
						//state.estimatedPrintTime = response.data.job.estimatedPrintTime/60
						sendEvent(name: "estimatedPrintTime", value: response.data.job.estimatedPrintTime/60 )
					} else {
						sendEvent(name: "estimatedPrintTime", value: 0 )
					}
				
				if (state.isPrinting && response.data.job.file.name != null)
					{
						//state.name = response.data.job.file.name
						sendEvent(name: "name", value: response.data.job.file.name )
					} else {
						sendEvent(name: "name", value: "none" )
					}
				if (response.data.job.user != null)
					{
						//state.user = response.data.job.user
						sendEvent(name: "user", value: response.data.job.user )
					} else {
						sendEvent(name: "user", value: "none" )
					}
				
				if (logEnable) log.debug "GetPrinterJobReturn"
				if (logEnable) log.info response.data
				toReturn = response.data.toString()


				// check printer temperatures after successful return of printer job details
				if(state.printerConnected){
					GetPrinterTemp()
					if(enableDisplayLayerProgress){
						GetDisplayLayerProgress()
					}
				} else {
					ResetTemperatures()
				}
			}
			else
			{
				log.warn "${response?.status}"
				sendEvent(name: "stateMessage", value: "${response?.status}")
				// set default status for values
				PrinterNotResponding()
			}
			// set octoprintConnected to true if receive any response
			state.octoprintConnected = true
		}
    } catch (Exception e){
        log.info e
        toReturn = e.toString()
		sendEvent(name: "stateMessage", value: toReturn)
		
		// set octoprintConnected to false if no response received
		state.octoprintConnected = false
		// set default status for values
		PrinterNotResponding()
    }


	// run fast check if state.isPrinting == true or if tool0-actual > 50  (extruder 1 is over 50C)
	unschedule(CheckPrinter)
	def currentToolTemp = device.currentValue("tool0-actual")?.toInteger() ?: 0
	if(state.isPrinting || currentToolTemp > 50){
		if (autoUpdate) runIn(delayCheckPrinting.toInteger(), CheckPrinter)
	} else {
		if (autoUpdate) runIn(delayCheckIdle.toInteger(), CheckPrinter)
	}
    return toReturn
}

def PrinterNotResponding(){
	sendEvent(name: "presence", value: "not present")
	state.printerConnected = false
	sendEvent(name: "state", value: "Disconnected")
	state.state = "Disconnected"
	state.isPrinting = false
	sendEvent(name: "completion", value: 0 )
	sendEvent(name: "printTimeLeft", value: 0 )
	sendEvent(name: "printTime", value: 0 )
	sendEvent(name: "estimatedPrintTime", value: 0 )
	sendEvent(name: "name", value: "none" )
	sendEvent(name: "user", value: "none" )
	// reset temperature readings to 0 when disconnected
	ResetTemperatures()
	// reset DLP addon readings to 0 when disconnected
	ResetDisplayLayerProgress()
}

def ResetTemperatures(){
	sendEvent(name: "bed-actual", value: 0 )
	sendEvent(name: "bed-offset", value: 0 )
	sendEvent(name: "bed-target", value: 0 )
	sendEvent(name: "tool0-actual", value: 0 )
	sendEvent(name: "tool0-offset", value: 0 )
	sendEvent(name: "tool0-target", value: 0 )

	if(device.currentValue("tool1-actual") != null){
		sendEvent(name: "tool1-actual", value: 0 )
		sendEvent(name: "tool1-offset", value: 0 )
		sendEvent(name: "tool1-target", value: 0 )
	}
	if(device.currentValue("tool2-actual") != null){
		sendEvent(name: "tool2-actual", value: 0 )
		sendEvent(name: "tool2-offset", value: 0 )
		sendEvent(name: "tool2-target", value: 0 )
	}		
}

def ResetDisplayLayerProgress(){
	// only reset if enabled AND if there is a previous value for DLP_fanSpeed
	if(enableDisplayLayerProgress && device.currentValue("DLP_fanSpeed") != null){
		sendEvent(name: "DLP_fanSpeed", value: 0 )
		sendEvent(name: "DLP_feedrate", value: 0 )
		sendEvent(name: "DLP_feedrateG0", value: 0 )
		sendEvent(name: "DLP_feedrateG1", value: 0 )
		sendEvent(name: "DLP_layer_averageLayerDuration", value: 0 )
		sendEvent(name: "DLP_layer_averageLayerDurationInSeconds", value: 0 )
		sendEvent(name: "DLP_layer_current", value: 0 )
		sendEvent(name: "DLP_layer_lastLayerDuration", value: 0 )
		sendEvent(name: "DLP_layer_lastLayerDurationInSeconds", value: 0 )
		sendEvent(name: "DLP_layer_total", value: 0 )
		sendEvent(name: "DLP_print_changeFilamentCount", value: 0 )
		sendEvent(name: "DLP_print_changeFilamentTimeLeft", value: "-" )
		sendEvent(name: "DLP_print_changeFilamentTimeLeftInSeconds", value: 0 )
		sendEvent(name: "DLP_print_estimatedChangedFilamentTime", value: "-" )
		sendEvent(name: "DLP_print_estimatedEndTime", value: 0 )
		sendEvent(name: "DLP_print_m73progress", value: null )
		sendEvent(name: "DLP_print_printerState", value: null )
		sendEvent(name: "DLP_print_progress", value: 0 )
		sendEvent(name: "DLP_print_timeLeft", value: 0 )
		sendEvent(name: "DLP_print_timeLeftInSeconds", value: 0 )
		sendEvent(name: "DLP_height_current", value: 0 )
		sendEvent(name: "DLP_height_currentFormatted", value: 0 )
		sendEvent(name: "DLP_height_total", value: 0 )
		sendEvent(name: "DLP_height_totalFormatted", value: 0 )
	}
}

def GetPrinterTemp(){
    if (logEnable) log.debug "GetPrinterTemp"
    def wxURI2 = "http://${ip_addr}:${url_port}/api/printer"
    def toReturn = " "
        
    def requestParams2 =
	[
		uri:  wxURI2,
        headers: [ 
                   "User-Agent": "Wget/1.20.1",
                   Accept: "*/*",
                   "Accept-Encoding": "identity",
                   Host: "${ip_addr}",
                   Connection: "Keep-Alive",
                   "X-Api-Key": "${api_key}",
                 ],
	]
    asynchttpGet('GetPrinterTempReturn', requestParams2)
}

def GetPrinterTempReturn(response, data) {
	if (logEnable) log.debug "GetPrinterTempReturn"
	if(response?.status == 200){
		if (logEnable) log.info response.data
		def R = response.getJson()
		def value = ""
		def event
		
		// just processing temperature values
		if(R.temperature != null){
			R.temperature.each{ t ->
				if(t != null){
					t.getValue().each { i ->
						value = "${t.getKey()}-${i}"
						event = value.split("=")
						sendEvent(name: event[0], value: event[1] )
					}
				}
			}
		}
	} else {
		log.warn "Did not get proper response for GetPrinterTemp"
		log.warn "${response?.status}"
	}
}

def GetDisplayLayerProgress(){
	if(!enableDisplayLayerProgress){
		log.warn "GetDisplayLayerProgress called, but enableDisplayLayerProgress is disabled"
		return
	} else {
		if (logEnable) log.debug "GetDisplayLayerProgress"
	}
	
    def wxURI2 = "http://${ip_addr}:${url_port}/plugin/DisplayLayerProgress/values"
    def requestParams2 =
	[
		uri:  wxURI2,
        headers: [ 
                   "User-Agent": "Wget/1.20.1",
                   Accept: "*/*",
                   "Accept-Encoding": "identity",
                   Host: "${ip_addr}",
                   Connection: "Keep-Alive",
                   "X-Api-Key": "${api_key}",
                 ],
	]
    asynchttpGet('GetDisplayLayerProgressReturn', requestParams2)
}

def GetDisplayLayerProgressReturn(response, data) {
	if (logEnable) log.debug "GetDisplayLayerProgressReturn"
	if(response?.status == 200){
		if (logEnable) log.info response.data
		def R = response.getJson()
		def value = ""
		def event = ""

		// process top level data
		if(R.fanSpeed != null){
			sendEvent(name: "DLP_fanSpeed", value: R.fanSpeed)
		}
		if(R.feedrate != null){
			sendEvent(name: "DLP_feedrate", value: R.feedrate)
		}
		if(R.feedrateG0 != null){
			sendEvent(name: "DLP_feedrateG0", value: R.feedrateG0)
		}
		if(R.feedrateG1 != null){
			sendEvent(name: "DLP_feedrateG1", value: R.feedrateG1)
		}

		// process layer data
		if(R.layer != null){
			R.layer.each{ t ->
				if(t != null){
					sendEvent(name: "DLP_layer_" + t.getKey(), value: t.getValue())
				}
			}
		}

		// process print data
		if(R.print != null){
			R.print.each{ t ->
				if(t != null){
					sendEvent(name: "DLP_print_" + t.getKey(), value: t.getValue())
				}
			}
		}

		// process height data
		if(R.height != null){
			R.height.each{ t ->
				if(t != null){
					sendEvent(name: "DLP_height_" + t.getKey(), value: t.getValue())
				}
			}
		}
	} else {
		log.warn "Did not get proper response for GetDisplayLayerProgress"
		log.warn "${response?.status}"
	}
}

def Print(){
    SendCommand("{ \"command\": \"start\" }","/api/job")
}
def Cancel(){
    SendCommand("{ \"command\": \"cancel\" }","/api/job")
}
def Restart(){
    SendCommand("{ \"command\": \"restart\" }","/api/job")
}
def Pause(){
    SendCommand('{"command":"pause","action":"pause"}',"/api/job")
}
def Resume(){
    SendCommand('{"command":"pause","action":"resume"}',"/api/job")
}
def PauseToggle(){
    SendCommand('{"command":"pause","action":"toggle"}',"/api/job")
}
def ConnectToPrinter(){
	if(state.printerConnected == false){
		SendCommand("{ \"command\": \"connect\" }","/api/connection")
	} else {
		log.debug "Attempting ConnectToPrinter, but state.printerConnected == true"
	}
}

def SendCommand(String payload, String path) {
    def headers = [:] 
    headers.put("HOST", "${ip_addr}:${url_port}")
    headers.put("Content-Type", "application/json")
    headers.put("X-Api-Key", "${api_key}")
    
    try {
        def hubAction = new hubitat.device.HubAction(
            method: "POST",
            path: path,
            body: payload,
            headers: headers
            )
        //log.debug hubAction
        return hubAction
    }
    catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}