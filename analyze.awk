BEGIN {
    OFS=";"
}

{
    gsub(/"/,"");
    client=$1;
    domain=$2;
    url=$7;
    status=$9;
    bytes=$10;
    upstream=$(NF-2);
    time=$NF;
    #gsub(/"/,"");
    printf "%.6f\n", $NF;
    
}

END {


}
