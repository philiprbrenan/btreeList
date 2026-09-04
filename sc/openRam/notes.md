Run docker image connected to the local file system

```
docker run -it --rm  -v/home/phil/btreeList/verilog:/home/phil/btreeList/verilog -w/home/phil/btreeList/verilog ghcr.io/philiprbrenan/or_local:latest  bash
```

Compile a ROM:

python3 /opt/OpenRAM/rom_compiler.py RomPrimes.py

RomPrimes.py

```
word_size           = 1

check_lvsdrc        = True

rom_data            = "includes/RomPrimes.hex"
data_type           = "hex"

output_name         = "RomPrimes"
output_path         = "macro/RomPrimes"

tech_name           = "sky130"
nominal_corner_only = True

route_supplies      = "ring"
check_lvsdrc        = True
```

Include file:
```
01020305070b0d
```
