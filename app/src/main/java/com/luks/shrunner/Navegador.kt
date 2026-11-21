                    pos == iData -> {
                        val d = File("/storage/emulated/0/Android/data")
                        if (d.exists()) navFile(d, destino)
                        else navShell("/storage/emulated/0/Android/data", destino)
                    }
