package com.analogvault.util

import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.math.pow

object Constants {
    val STORAGE_TYPES = listOf("Shelf","Fridge","Freezer","Cool Dark Place","Custom")
    val FILM_TYPES = listOf("Color Negative (C-41)","Black & White","Slide (E-6)","Infrared","Instant")
    val ISOS = listOf(25,50,64,100,160,200,400,800,1600,3200,6400)
    val SHUTTER_SPEEDS = listOf(
        "1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30",
        "1/15","1/8","1/4","1/2","1s","2s","4s","8s","15s","30s","B"
    )
    val APERTURES = listOf(1.0,1.2,1.4,1.8,2.0,2.8,3.5,4.0,5.6,8.0,11.0,16.0,22.0,32.0)
    val METERING_TYPES = listOf("Evaluative/Matrix","Center-Weighted","Spot","Highlight-Weighted")
    val DEVELOP_PROCESSES = listOf("C-41 (Color)","B&W (Standard)","B&W (Stand)","B&W (Semi-Stand)","E-6 (Slide)","Custom")
    val SCAN_METHODS = listOf("Flatbed Scanner","DSLR/Mirrorless","Phone Scan","Lab Scan","Drum Scan")
    val ACCESSORY_TYPES = listOf("Filter","Flash","Tripod","Cable Release","Light Meter","Film Changing Bag","Darkroom Equipment","Bag/Case","Strap","Lens Hood","Other")
    val CHEM_TYPES = listOf("Developer","Fixer","Blix","Bleach","Stop Bath","Stabiliser","Wetting Agent","Custom")
    val CHEM_UNITS = listOf("ml","L","g","oz","mixed (L)")
    val CAMERA_FORMATS = listOf("35mm","120 (MF)","4x5","110","Instant")
    val CONDITIONS = listOf("Mint","Excellent","Good","Fair","Needs CLA")
    val LENS_CONDITIONS = listOf("Mint","Excellent","Good","Fair")

    val FILM_DB = listOf(
        "Kodak Portra 160","Kodak Portra 400","Kodak Portra 800","Kodak Ektar 100","Kodak ColorPlus 200",
        "Kodak UltraMax 400","Kodak Gold 200","Kodak T-Max 100","Kodak T-Max 400","Kodak Tri-X 400",
        "Kodak Double-X 250","Fujifilm Provia 100F","Fujifilm Velvia 50","Fujifilm Velvia 100",
        "Fujifilm Superia 200","Fujifilm Superia X-TRA 400","Fujifilm Acros 100 II","Ilford HP5 Plus 400",
        "Ilford FP4 Plus 125","Ilford Delta 100","Ilford Delta 400","Ilford Delta 3200","Ilford Pan F Plus 50",
        "Ilford XP2 Super 400","Lomography Color Negative 100","Lomography Color Negative 400",
        "Lomography Lady Grey 400","CineStill 50D","CineStill 800T","CineStill 400D","Rollei RPX 400",
        "Rollei Infrared 400","Fomapan 100","Fomapan 400","Kentmere Pan 100","Kentmere Pan 400",
        "Bergger Pancro 400","Adox CMS 20 II"
    )
    val CAMERA_DB = listOf(
        "Nikon F3","Nikon F4","Nikon FM2","Nikon FM3A","Nikon FE2","Nikon FA","Nikon F100",
        "Canon AE-1","Canon AE-1 Program","Canon A-1","Canon F-1","Canon EOS-1V",
        "Olympus OM-1","Olympus OM-2","Olympus OM-4Ti","Olympus XA","Olympus mju-II",
        "Pentax K1000","Pentax ME Super","Pentax LX","Minolta X-700","Minolta SRT 101",
        "Leica M6","Leica M7","Leica MP","Leica M3","Leica M2","Leica IIIf",
        "Voigtländer Bessa R2A","Hasselblad 500 C/M","Hasselblad 503CW","Hasselblad XPan",
        "Mamiya RB67","Mamiya RZ67","Mamiya 7 II","Rolleiflex 2.8F","Rolleiflex 3.5F",
        "Yashica Mat-124G","Bronica ETRSi","Contax T2","Contax G2","Lomo LC-A+"
    )
    val LENS_DB = listOf(
        "Nikkor 50mm f/1.4","Nikkor 50mm f/1.8","Nikkor 28mm f/2.8","Nikkor 85mm f/1.8","Nikkor 105mm f/2.5",
        "Canon FD 50mm f/1.4","Canon FD 50mm f/1.8","Canon FD 28mm f/2.8",
        "Olympus Zuiko 50mm f/1.4","Olympus Zuiko 50mm f/1.8",
        "Leica Summicron 50mm f/2","Leica Summilux 50mm f/1.4","Leica Elmarit 28mm f/2.8",
        "Voigtländer Nokton 50mm f/1.5","Voigtländer Nokton 35mm f/1.4",
        "Carl Zeiss Planar 50mm f/1.4","Carl Zeiss Distagon 35mm f/2",
        "SMC Takumar 50mm f/1.4","Super Takumar 55mm f/1.8","Helios 44-2 58mm f/2","Jupiter-9 85mm f/2"
    )
    val DEV_DB = listOf(
        "Kodak D-76","Kodak HC-110","Ilford ID-11","Ilford Ilfosol 3","Ilford Microphen",
        "Rodinal (R09)","Caffenol-C","Xtol","Pyrocat HD","PMK Pyro","Cinestill Df96 Monobath",
        "Kodak C-41 Kit","Tetenal Colortec C-41","Unicolor C-41","Cinestill CS41"
    )
    val FIX_DB = listOf(
        "Ilford Rapid Fixer","Kodak Rapid Fixer","Sprint Record Fixer","TF-4 Archival Fixer","Sodium Thiosulfate"
    )

    // Mount groups
    data class MountGroup(val native: List<String>, val adapters: List<String>)
    val MOUNT_GROUPS: Map<String, MountGroup> = mapOf(
        "Nikon F"         to MountGroup(listOf("Nikon F","Nikon AI","Nikon AIS","Nikon Non-AI"), listOf("M42","Canon FD","Contax/Yashica","Olympus OM","Minolta MD","Leica R","Pentax K","T2")),
        "Canon FD"        to MountGroup(listOf("Canon FD","Canon FL"),                            listOf("M42","T2")),
        "Canon EF"        to MountGroup(listOf("Canon EF","Canon EF-S"),                          listOf("M42","Nikon F","Leica R","Olympus OM","Contax/Yashica","Minolta MD","Pentax K","T2")),
        "M42"             to MountGroup(listOf("M42"),                                             listOf("T2")),
        "Olympus OM"      to MountGroup(listOf("Olympus OM"),                                     listOf("M42","T2")),
        "Minolta MD"      to MountGroup(listOf("Minolta MD","Minolta MC","Minolta SR"),            listOf("M42","T2")),
        "Pentax K"        to MountGroup(listOf("Pentax K","Pentax KA","Pentax KAF","Pentax M42"), listOf("M42","T2")),
        "Contax/Yashica"  to MountGroup(listOf("Contax/Yashica","C/Y"),                           listOf("M42","T2")),
        "Leica M"         to MountGroup(listOf("Leica M","Voigtländer VM","Zeiss ZM"),            listOf("Leica L39/LTM","T2")),
        "Leica R"         to MountGroup(listOf("Leica R"),                                         listOf("M42","T2")),
        "Leica L39/LTM"   to MountGroup(listOf("Leica L39/LTM","LTM","L39"),                     listOf("T2")),
        "Hasselblad V"    to MountGroup(listOf("Hasselblad V"),                                    listOf("T2")),
        "Mamiya 645"      to MountGroup(listOf("Mamiya 645"),                                      listOf("T2")),
        "Mamiya RB/RZ"    to MountGroup(listOf("Mamiya RB67","Mamiya RZ67"),                      listOf("T2")),
        "Bronica ETR"     to MountGroup(listOf("Bronica ETR"),                                     listOf("T2")),
        "T2"              to MountGroup(listOf("T2"),                                               emptyList()),
    )
    val COMMON_MOUNTS = MOUNT_GROUPS.keys.toList()

    fun mountCompat(cameraMount: String, lensMount: String, manualOverrides: List<String> = emptyList()): String {
        if (cameraMount.isBlank() || lensMount.isBlank()) return "unknown"
        if (cameraMount == lensMount) return "native"
        if (manualOverrides.contains(lensMount)) return "adapter"
        val group = MOUNT_GROUPS[cameraMount]
            ?: MOUNT_GROUPS.values.firstOrNull { it.native.contains(cameraMount) }
            ?: return "unknown"
        if (group.native.contains(lensMount)) return "native"
        if (group.adapters.contains(lensMount)) return "adapter"
        return "incompatible"
    }

    // EV helpers
    fun evalShutter(s: String): Double {
        if (s.isBlank()) return 1.0/125
        if (s.endsWith("s")) return s.dropLast(1).toDoubleOrNull() ?: 1.0
        if (s == "B") return 30.0
        val parts = s.split("/")
        return if (parts.size == 2) parts[0].toDouble() / parts[1].toDouble() else s.toDoubleOrNull() ?: 1.0/125
    }
    fun calcEV(iso: Int, shutter: String, aperture: Double): Double {
        val t = evalShutter(shutter)
        return log2(aperture * aperture / t) - log2(iso / 100.0)
    }
    fun calcAperture(iso: Int, shutter: String, targetEV: Double): Double {
        val t = evalShutter(shutter)
        val a2 = t * 2.0.pow(targetEV) * (iso / 100.0)
        return sqrt(maxOf(a2, 1.0))
    }
}
